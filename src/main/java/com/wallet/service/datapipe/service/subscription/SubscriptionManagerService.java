package com.wallet.service.datapipe.service.subscription;

import com.wallet.service.datapipe.client.quicknode.wss.SolanaWsClientService;
import com.wallet.service.datapipe.client.quicknode.wss.event.WebSocketConnectionEvent;
import com.wallet.service.datapipe.dto.quicknode.wss.LogsNotificationDto;
import com.wallet.service.datapipe.service.processor.RealtimeTransactionProcessorService;
import com.wallet.service.room.model.TradeRoom;
import com.wallet.service.room.service.TradeRoomService;
import com.wallet.service.utils.StringFormatUtils;

import ch.qos.logback.core.util.StringUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class SubscriptionManagerService implements ApplicationListener<WebSocketConnectionEvent> {

    private final SolanaWsClientService solanaWsClientService;
    private final TradeRoomService tradeRoomService;
    private final RealtimeTransactionProcessorService realtimeTransactionProcessorService;

    // Inner class to hold context for a wallet's subscription related to a specific room
    private static class RoomSubscriptionContext {
        String roomId;
        String targetTokenAddress;

        RoomSubscriptionContext(String roomId, String targetTokenAddress) {
            this.roomId = roomId;
            this.targetTokenAddress = targetTokenAddress;
        }

        // equals and hashCode are important if these objects are stored in a Set
        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            RoomSubscriptionContext that = (RoomSubscriptionContext) o;
            return roomId.equals(that.roomId) && targetTokenAddress.equals(that.targetTokenAddress);
        }

        @Override
        public int hashCode() {
            return roomId.hashCode() * 31 + targetTokenAddress.hashCode();
        }
    }

    // Tracks which wallet is subscribed to which set of room contexts
    // walletAddress -> Set<RoomSubscriptionContext>
    private final Map<String, Set<RoomSubscriptionContext>> walletRoomSubscriptions = new ConcurrentHashMap<>();

    // Tracks the consumer instance for each subscribed wallet to avoid recreating it unnecessarily
    // And to ensure the SolanaWsClientService has a stable consumer to call.
    private final Map<String, Consumer<LogsNotificationDto>> walletNotificationConsumers = new ConcurrentHashMap<>();


    public void handleUserJoinedRoom(String walletAddress, String roomId) {
        TradeRoom room = tradeRoomService.getTradeRoomByRoomId(roomId)
                .orElse(null); // Use the new method and handle Optional
        if (room == null || room.getStatus() != TradeRoom.RoomStatus.OPEN) {
            log.warn("处理用户加入房间 {} (钱包 {}) 失败: 房间未找到或未开放。", roomId, StringFormatUtils.formatAddress(walletAddress));
            return;
        }
        String targetTokenAddress = room.getTokenAddress();
        String targetTokenSymbol = StringUtil.isNullOrEmpty(room.getTokenSymbol()) ? "未知": room.getTokenSymbol();

        log.info("处理用户 {} 加入房间 {}。目标代币: {}({})", 
            StringFormatUtils.formatAddress(walletAddress), 
            roomId, 
            targetTokenSymbol, 
            StringFormatUtils.formatAddress(targetTokenAddress));

        Set<RoomSubscriptionContext> contexts = walletRoomSubscriptions.computeIfAbsent(walletAddress, k -> new HashSet<>());
        boolean added = contexts.add(new RoomSubscriptionContext(roomId, targetTokenAddress));

        if (added && !walletNotificationConsumers.containsKey(walletAddress)) {
            // This is the first time this wallet is being associated with any room,
            // or its previous subscriptions were cleared. We need to subscribe it.
            Consumer<LogsNotificationDto> consumer = createConsumerForWallet(walletAddress);
            walletNotificationConsumers.put(walletAddress, consumer);
            boolean subInitiated = solanaWsClientService.subscribeWalletLogs(walletAddress, consumer);
            if (subInitiated) {
                log.info("已为钱包 {} 发起日志订阅 (因加入房间 {})。", StringFormatUtils.formatAddress(walletAddress), roomId);
            } else {
                log.warn("为钱包 {} (房间 {}) 发起日志订阅失败。将在 WebSocket 连接成功后重试。", StringFormatUtils.formatAddress(walletAddress), roomId);
                // No need to remove consumer here, it will be used on reconnect.
            }
        } else if (added) {
            log.info("钱包 {} 已订阅 WebSocket。已为房间 {} 添加新的房间上下文。", StringFormatUtils.formatAddress(walletAddress), roomId);
        } else {
            log.info("钱包 {} 已在跟踪房间 {} (代币 {})。WebSocket 订阅无变化。", 
                StringFormatUtils.formatAddress(walletAddress), 
                roomId, 
                StringFormatUtils.formatAddress(targetTokenAddress));
        }
    }

    public void handleUserLeftRoom(String walletAddress, String roomId) {
        log.info("处理用户 {} 离开房间 {}。", StringFormatUtils.formatAddress(walletAddress), roomId);
        Set<RoomSubscriptionContext> contexts = walletRoomSubscriptions.get(walletAddress);
        if (contexts != null) {
            // Find the specific context to remove. This requires TradeRoom details if token can change,
            // but for now, assuming roomId is enough if token is static per room.
            // If token can change, the RoomSubscriptionContext should be identified more robustly.
            boolean removed = contexts.removeIf(context -> context.roomId.equals(roomId));

            if (removed) {
                log.info("已从钱包 {} 中移除房间 {} 的上下文。", StringFormatUtils.formatAddress(walletAddress), roomId);
            }

            if (contexts.isEmpty()) {
                walletRoomSubscriptions.remove(walletAddress);
                walletNotificationConsumers.remove(walletAddress); // Remove consumer when no more rooms are tracked by this wallet
                solanaWsClientService.unsubscribeWalletLogs(walletAddress);
                log.info("钱包 {} 已不在任何跟踪的房间中。已取消 WebSocket 订阅。", StringFormatUtils.formatAddress(walletAddress));
            }
        } else {
            log.warn("尝试处理用户 {} 离开房间 {} 时未找到其房间上下文。未执行任何操作。", StringFormatUtils.formatAddress(walletAddress), roomId);
        }
    }

    public void handleRoomClosed(String roomId) {
        TradeRoom room = tradeRoomService.getTradeRoomByRoomId(roomId)
                .orElse(null); // Use the new method and handle Optional
        if (room == null) {
            log.warn("处理房间 {} 关闭失败: 房间未找到。", roomId);
            return;
        }
        log.info("处理房间 {} 关闭。正在取消所有成员的订阅。", roomId);
        // Find all wallets that were part of this room
        // This is a bit inefficient as it iterates all wallet subscriptions.
        // A reverse map (roomId -> Set<walletAddress>) might be useful if this is frequent.
        walletRoomSubscriptions.forEach((walletAddress, contexts) -> {
            boolean removed = contexts.removeIf(context -> context.roomId.equals(roomId));
            if (removed) {
                log.info("已从钱包 {} 中移除已关闭房间 {} 的上下文。", StringFormatUtils.formatAddress(walletAddress), roomId);
                if (contexts.isEmpty()) {
                    walletRoomSubscriptions.remove(walletAddress);
                    walletNotificationConsumers.remove(walletAddress);
                    solanaWsClientService.unsubscribeWalletLogs(walletAddress);
                    log.info("钱包 {} 已不在任何跟踪的房间中 (因房间 {} 关闭)。已取消 WebSocket 订阅。", StringFormatUtils.formatAddress(walletAddress), roomId);
                }
            }
        });
    }

    private Consumer<LogsNotificationDto> createConsumerForWallet(String walletAddress) {
        // Capture the formatted wallet address for use in the lambda
        final String formattedWalletAddressForConsumer = StringFormatUtils.formatAddress(walletAddress); 
        return notification -> {
            if (notification == null || notification.getParams() == null || notification.getParams().getResult() == null || notification.getParams().getResult().getValue() == null) {
                log.warn("收到钱包 {} 不完整的 LogsNotificationDto。已跳过处理。", formattedWalletAddressForConsumer);
                return;
            }

            LogsNotificationDto.ValueDto logValue = notification.getParams().getResult().getValue();
            String signature = logValue.getSignature();
            String formattedSignature = (signature == null ? "N/A" : StringFormatUtils.formatAddress(signature));
            Object error = logValue.getErr();

            if (error != null) {
                log.debug("钱包 {} 的交易 {} 执行失败。已跳过处理。错误: {}", formattedWalletAddressForConsumer, formattedSignature, error);
                return;
            }

            if (logValue.getSignature() == null || logValue.getSignature().isEmpty()) {
                log.warn("收到钱包 {} 的通知，但缺少交易签名。已跳过处理。", formattedWalletAddressForConsumer);
                return;
            }

            Set<RoomSubscriptionContext> relevantContexts = walletRoomSubscriptions.getOrDefault(walletAddress, Collections.emptySet());
            if (relevantContexts.isEmpty()) {
                log.warn("处理交易 {} 时，未找到钱包 {} 的活动房间上下文。可能在处理前刚好取消了订阅。", formattedSignature, formattedWalletAddressForConsumer);
                return;
            }

            log.debug("钱包 {} 收到交易 {} 的日志。上下文数量: {}。正在转发给 RealtimeTransactionProcessorService。",
                    formattedWalletAddressForConsumer, formattedSignature, relevantContexts.size());

            List<RealtimeTransactionProcessorService.RoomTarget> roomTargets = relevantContexts.stream()
               .map(ctx -> new RealtimeTransactionProcessorService.RoomTarget(ctx.roomId, ctx.targetTokenAddress)) // Corrected RoomTarget instantiation
               .collect(Collectors.toList());

            realtimeTransactionProcessorService.processLogNotification(
                logValue, 
                walletAddress, 
                roomTargets
            );
        };
    }

    @Override
    public void onApplicationEvent(WebSocketConnectionEvent event) {
        if (event.isConnected()) {
            log.info("WebSocket 连接已建立。正在重新评估并确保所有必要的钱包订阅...");
            // When connection is (re)established, iterate through all wallets that *should* be subscribed
            // and ensure their subscriptions are active.
            walletRoomSubscriptions.forEach((walletAddress, contexts) -> {
                if (!contexts.isEmpty()) {
                    final String formattedWalletAddressForLoop = StringFormatUtils.formatAddress(walletAddress); 
                    if (!walletNotificationConsumers.containsKey(walletAddress)) {
                        log.warn("WebSocket 连接成功后，为钱包 {} 重新创建消费者回调，因其之前缺失。", formattedWalletAddressForLoop);
                        walletNotificationConsumers.put(walletAddress, createConsumerForWallet(walletAddress));
                    }
                    Consumer<LogsNotificationDto> consumer = walletNotificationConsumers.get(walletAddress);
                    // log.info("作为 WebSocket 重连的一部分，尝试为钱包 {} 重新订阅。", formattedWalletAddressForLoop);
                    boolean subInitiated = solanaWsClientService.subscribeWalletLogs(walletAddress, consumer);
                     if (subInitiated) {
                        log.info("WebSocket 重连后，已成功为钱包 {} 重新发起日志订阅。", formattedWalletAddressForLoop);
                    } else {
                        log.warn("WebSocket 重连后，为钱包 {} 重新发起日志订阅失败。", formattedWalletAddressForLoop);
                    }
                }
            });
        } else {
            log.warn("WebSocket 连接丢失。活动订阅将在重新连接后重建。");
            // SolanaWsClientService already clears its internal maps on disconnect.
            // We keep our walletRoomSubscriptions and walletNotificationConsumers intact,
            // so we know what to re-subscribe on connection_opened.
        }
    }

} 