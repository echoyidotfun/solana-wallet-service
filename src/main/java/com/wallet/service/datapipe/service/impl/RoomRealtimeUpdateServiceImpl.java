package com.wallet.service.datapipe.service.impl;

import com.wallet.service.datapipe.dto.AnalyzedWalletAction;
import com.wallet.service.datapipe.service.RoomRealtimeUpdateService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class RoomRealtimeUpdateServiceImpl implements RoomRealtimeUpdateService {

    private final SimpMessagingTemplate messagingTemplate;

    @Override
    public void sendActionUpdate(String roomId, AnalyzedWalletAction action) {
        String destination = String.format("/topic/room/%s/transactions", roomId);
        try {
            // 在实际发送之前，可以考虑对action进行一些转换或包装，例如，确保所有需要短地址的字段都已处理
            // 不过，当前DTO的定义中，地址字段是原始的。
            // 假设 AnalyzedWalletAction DTO 可以直接序列化并发送
            messagingTemplate.convertAndSend(destination, action);
            log.info("已向房间 {} 的主题 {} 推送钱包操作: {} (签名: {})", 
                roomId, 
                destination, 
                action.getWalletAddress(), // 这里的地址是原始的
                action.getTransactionSignature() // 这里的签名是原始的
            );
        } catch (Exception e) {
            log.error("向房间 {} 推送钱包操作至 {} 失败。Action: {}, Wallet: {}, Signature: {}. Error: {}",
                roomId, 
                destination, 
                action,
                action.getWalletAddress(),
                action.getTransactionSignature(),
                e.getMessage(), 
                e);
        }
    }
} 