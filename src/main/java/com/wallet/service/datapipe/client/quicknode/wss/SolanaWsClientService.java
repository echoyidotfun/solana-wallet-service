package com.wallet.service.datapipe.client.quicknode.wss;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wallet.service.datapipe.client.quicknode.wss.event.WebSocketConnectionEvent;
import com.wallet.service.datapipe.dto.quicknode.wss.*;
import com.wallet.service.utils.StringFormatUtils;

import lombok.extern.slf4j.Slf4j;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

@Service
@Slf4j
public class SolanaWsClientService {

    @Value("${quicknode.wss}")
    private String wssUrl;

    @Value("${quicknode.api-key}")
    private String apiKey;

    private WebSocketClient client;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final AtomicLong requestIdCounter = new AtomicLong(System.currentTimeMillis());
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private final ApplicationEventPublisher eventPublisher;

    // Holds information about a pending or active subscription
    private static class SubscriptionDetails {
        String walletAddress; // Wallet address this subscription is for
        Consumer<LogsNotificationDto> notificationConsumer; // Callback to invoke when a notification for this wallet arrives

        SubscriptionDetails(String walletAddress, Consumer<LogsNotificationDto> notificationConsumer) {
            this.walletAddress = walletAddress;
            this.notificationConsumer = notificationConsumer;
        }
    }

    // Maps request ID to SubscriptionDetails for subscriptions awaiting QN subscription ID confirmation
    private final Map<Long, SubscriptionDetails> pendingSubscriptions = new ConcurrentHashMap<>();
    // Maps QuickNode's subscription ID to SubscriptionDetails for active, confirmed subscriptions
    private final Map<Long, SubscriptionDetails> activeSubscriptionsByQnId = new ConcurrentHashMap<>();
    // Maps a wallet address to its active QuickNode subscription ID for quick lookup and to prevent duplicate subscriptions
    private final Map<String, Long> activeQnIdByWallet = new ConcurrentHashMap<>();

    public SolanaWsClientService(ApplicationEventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
    }

    @PostConstruct
    public void init() {
        connect();
        // Check connection status periodically and attempt to reconnect if down
        scheduler.scheduleAtFixedRate(this::ensureConnected, 30, 30, TimeUnit.SECONDS);
    }

    private synchronized void connect() {
        if (client != null && client.isOpen()) {
            log.trace("WebSocket 客户端已连接并打开。");
            return;
        }
        try {
            String fullWssUrl = wssUrl + apiKey;
            log.info("尝试连接到 WebSocket: {}", getRedactedUrl(fullWssUrl));
            client = new WebSocketClient(new URI(fullWssUrl)) {
                @Override
                public void onOpen(ServerHandshake handshakedata) {
                    log.info("WebSocket 连接已打开。状态: {}, HTTP 状态码: {}", handshakedata.getHttpStatusMessage(), handshakedata.getHttpStatus());
                    eventPublisher.publishEvent(new WebSocketConnectionEvent(this, true));
                }

                @Override
                public void onMessage(String message) {
                    handleIncomingMessage(message);
                }

                @Override
                public void onClose(int code, String reason, boolean remote) {
                    log.warn("WebSocket 连接已关闭。代码: {}, 原因: {}, 是否远程关闭: {}", code, reason, remote);
                    activeQnIdByWallet.clear(); // Clear active subscriptions as they are no longer valid
                    activeSubscriptionsByQnId.clear();
                    pendingSubscriptions.clear(); // Clear pending ones too as new requests will be needed
                    eventPublisher.publishEvent(new WebSocketConnectionEvent(this, false));
                }

                @Override
                public void onError(Exception ex) {
                    log.error("WebSocket 客户端遇到错误: {}", ex.getMessage());
                    // onClose will usually be called after an error that closes the connection
                }
            };
            client.connect(); // This is non-blocking
        } catch (URISyntaxException e) {
            log.error("无效的 WebSocket URI 语法: {}. 错误: {}", getRedactedUrl(wssUrl), e.getMessage());
        } catch (Exception e) {
            log.error("初始化或连接 WebSocket 客户端失败: {}", e.getMessage(), e);
        }
    }

    private String getRedactedUrl(String originalUrl) {
        if (originalUrl == null || apiKey == null || apiKey.isEmpty()) {
            return originalUrl;
        }
        return originalUrl.replace(apiKey, "<API密钥已隐藏>");
    }

    private synchronized void ensureConnected() {
        if (client == null || !client.isOpen()) {
            if (client != null && (client.isClosing() || client.isClosed())) {
                 log.warn("WebSocket 正在关闭或已关闭，尝试重新连接...");
            } else {
                 log.info("WebSocket 未连接。尝试连接中...");
            }
            connect();
        } else {
            log.trace("WebSocket 连接检查: 活动并已打开。");
        }
    }

    private void handleIncomingMessage(String message) {
        log.trace("收到 WebSocket 消息: {}", message);
        try {
            if (message.contains("\"method\":\"logsNotification\"")) {
                LogsNotificationDto notification = objectMapper.readValue(message, LogsNotificationDto.class);
                if (notification != null && notification.getParams() != null) {
                    SubscriptionDetails details = activeSubscriptionsByQnId.get(notification.getParams().getSubscription());
                    if (details != null) {
                        if (details.notificationConsumer != null) {
                            details.notificationConsumer.accept(notification);
                        } else {
                            log.warn("没有为 QuickNode 订阅 ID {} 配置消费者回调", notification.getParams().getSubscription());
                        }
                    } else {
                        log.warn("收到未知或非活动的 QuickNode 订阅 ID {} 的日志通知", notification.getParams().getSubscription());
                    }
                }
            } else if (message.contains("\"id\":")) { // All RPC responses should have an ID
                WebSocketResponseDto response = objectMapper.readValue(message, WebSocketResponseDto.class);
                if (response.getError() != null) {
                    log.error("请求 ID {} 的 WebSocket RPC 错误: 代码 {}, 消息 {}",
                            response.getId(), response.getError().getCode(), response.getError().getMessage());
                    pendingSubscriptions.remove(response.getId());
                } else if (response.getResult() != null) {
                    Object resultValue = response.getResult();
                    SubscriptionDetails pendingDetail = pendingSubscriptions.remove(response.getId());

                    if (resultValue instanceof Number && pendingDetail != null) {
                        // This was a logsSubscribe confirmation
                        long qnSubscriptionId = ((Number) resultValue).longValue();
                        activeSubscriptionsByQnId.put(qnSubscriptionId, pendingDetail);
                        activeQnIdByWallet.put(pendingDetail.walletAddress, qnSubscriptionId);
                        log.info("已成功为钱包 {} 订阅，QuickNode 订阅 ID: {}",
                                 StringFormatUtils.formatAddress(pendingDetail.walletAddress), qnSubscriptionId);
                    } else if (resultValue instanceof Boolean && ((Boolean) resultValue)) {
                        // This was a logsUnsubscribe confirmation
                        log.info("已成功取消订阅 WebSocket 响应，请求 ID: {}。结果: {}", response.getId(), resultValue);
                        // No further action needed for activeSubscriptionsByQnId or activeQnIdByWallet
                        // as they are typically cleared *before* sending the unsubscribe request in unsubscribeWalletLogs.
                    } else if (pendingDetail == null && resultValue instanceof Boolean && ((Boolean) resultValue)) {
                        // This case handles unsubscribe confirmations where pendingDetail might be null (already processed or not applicable)
                        log.info("收到请求 ID {} 的成功取消订阅 WebSocket RPC 响应: 结果 {}", response.getId(), resultValue);
                    } else {
                        log.warn("收到请求 ID {} 的成功 WebSocket RPC 响应，但结果类型未明确处理或无待处理详情: 结果 {}，类型: {}. 关联的pendingDetail是否存在: {}",
                                 response.getId(), resultValue, resultValue != null ? resultValue.getClass().getName() : "null", pendingDetail != null);
                        if (pendingDetail != null) {
                             log.warn("请求 ID {} (钱包: {}) 有待处理的订阅详情，但收到的结果类型不是预期的 Number (Long)。实际结果: {}, 类型: {}. 该待处理订阅已被移除。", 
                                      response.getId(), StringFormatUtils.formatAddress(pendingDetail.walletAddress), resultValue, resultValue.getClass().getName());
                        }
                    }
                }
            } else {
                log.warn("收到未处理的 WebSocket 消息类型 (不含 method 或 id): {}", message);
            }
        } catch (JsonProcessingException e) {
            log.error("解析 WebSocket 消息 JSON 失败: {}", message, e);
        } catch (Exception e) {
            log.error("处理传入的 WebSocket 消息时发生通用错误: {}", message, e);
        }
    }

    public boolean subscribeWalletLogs(String walletAddress, Consumer<LogsNotificationDto> notificationConsumer) {
        if (activeQnIdByWallet.containsKey(walletAddress)) {
            log.info("钱包 {} 已处于活动订阅状态。当前 QuickNode ID: {}。跳过新的订阅请求。", 
                     StringFormatUtils.formatAddress(walletAddress), activeQnIdByWallet.get(walletAddress));
            return true;
        }
        if (client == null || !client.isOpen()) {
            log.warn("WebSocket 客户端未连接。钱包 {} 的订阅请求未发送。将在 WebSocketConnectionEvent(true) 事件后由管理器尝试。", StringFormatUtils.formatAddress(walletAddress));
            return false;
        }

        long requestId = requestIdCounter.getAndIncrement();
        LogsFilterDto filter = new LogsFilterDto(Collections.singletonList(walletAddress));
        RpcLogsFilterConfigDto config = new RpcLogsFilterConfigDto("finalized", "jsonParsed");
        List<Object> paramsList = Arrays.asList(filter, config);
        WebSocketRequestDto request = new WebSocketRequestDto("2.0", requestId, "logsSubscribe", paramsList);

        try {
            String jsonRequest = objectMapper.writeValueAsString(request);
            pendingSubscriptions.put(requestId, new SubscriptionDetails(walletAddress, notificationConsumer));
            log.info("为钱包 {} 发送 logsSubscribe 请求 (请求 ID: {}): {}", StringFormatUtils.formatAddress(walletAddress), requestId, jsonRequest);
            client.send(jsonRequest);
            return true;
        } catch (JsonProcessingException e) {
            log.error("序列化钱包 {} 的 logsSubscribe 请求失败: {}", StringFormatUtils.formatAddress(walletAddress), e.getMessage());
            pendingSubscriptions.remove(requestId);
            return false;
        }
    }

    public void unsubscribeWalletLogs(String walletAddress) {
        Long qnSubscriptionId = activeQnIdByWallet.remove(walletAddress);
        if (qnSubscriptionId == null) {
            log.debug("无法取消订阅钱包 {}: 在本地活动的钱包到QuickNodeID映射中未找到。", StringFormatUtils.formatAddress(walletAddress));
            return;
        }
        activeSubscriptionsByQnId.remove(qnSubscriptionId);
        log.info("已在本地移除钱包 {} 的活动订阅 (QuickNode 订阅 ID: {}). 正在向 QuickNode 发送取消订阅请求。", StringFormatUtils.formatAddress(walletAddress), qnSubscriptionId);

        if (client == null || !client.isOpen()) {
            log.warn("WebSocket 客户端未连接。无法为 QuickNode 订阅 ID {} 发送 logsUnsubscribe 请求。该订阅已在本地移除。", qnSubscriptionId);
            return;
        }

        long requestId = requestIdCounter.getAndIncrement();
        List<Object> paramsList = Collections.singletonList(qnSubscriptionId);
        WebSocketRequestDto request = new WebSocketRequestDto("2.0", requestId, "logsUnsubscribe", paramsList);
        try {
            String jsonRequest = objectMapper.writeValueAsString(request);
            log.info("为 QuickNode 订阅 ID {} (钱包: {}) 发送 logsUnsubscribe 请求: {}", qnSubscriptionId, StringFormatUtils.formatAddress(walletAddress), jsonRequest);
            client.send(jsonRequest);
        } catch (JsonProcessingException e) {
            log.error("序列化 QuickNode 订阅 ID {} 的 logsUnsubscribe 请求失败: {}", qnSubscriptionId, e.getMessage());
        }
    }

    @PreDestroy
    public void destroy() {
        log.info("正在关闭 SolanaWsClientService 和 WebSocket 连接...");
        scheduler.shutdownNow(); // Stop the ensureConnected task
        if (client != null) {
            try {
                if (client.isOpen()) {
                    log.info("正在关闭 WebSocket 连接。当前活动 QuickNode 订阅数: {}", activeSubscriptionsByQnId.size());
                    // Note: QuickNode might auto-handle unsubscriptions on disconnect, but explicit is cleaner if API supports batch unsubscribe.
                    // For now, we are unsubscribing one by one via activeQnIdByWallet removal in other methods, or rely on close.
                }
                client.closeBlocking(); // Wait for the connection to close
            } catch (InterruptedException e) {
                log.error("关闭 WebSocket 客户端时被中断。", e);
                Thread.currentThread().interrupt(); // Preserve interrupt status
            } catch (Exception e) {
                log.error("在 @PreDestroy 中关闭 WebSocket 客户端 (closeBlocking) 时发生异常: {}", e.getMessage());
            }
        }
        log.info("SolanaWsClientService 已关闭。");
    }
} 