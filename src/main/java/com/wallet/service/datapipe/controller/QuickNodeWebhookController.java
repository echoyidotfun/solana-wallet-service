package com.wallet.service.datapipe.controller;

import com.wallet.service.datapipe.service.QuickNodeWebhookService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/webhook/quicknode")
@Slf4j
@RequiredArgsConstructor
public class QuickNodeWebhookController {

    private final QuickNodeWebhookService quicknodeWebhookService;

    /**
     * 处理来自QuickNode的交易事件webhook
     * 
     * @param events 包含交易事件数据的JSON字符串
     * @return 确认响应
     */
    @PostMapping("/transactions")
    public ResponseEntity<String> handleQuickNodeTransactionEvents(@RequestBody String events) {
        if (events == null || events.isEmpty()) {
            log.warn("收到空的QuickNode webhook事件");
            return ResponseEntity.ok("No events received");
        }

        try {
            // 处理交易事件
            quicknodeWebhookService.processQuickNodeEvents(events);
            return ResponseEntity.ok("Events processed successfully");
        } catch (Exception e) {
            log.error("处理QuickNode交易事件时发生错误: {}", e.getMessage(), e);
            
            // 这防止QuickNode重试发送相同事件
            return ResponseEntity.ok("Error processed, but acknowledged");
        }
    }

    /**
     * 用于QuickNode在设置webhook时验证端点
     */
    @GetMapping("/transactions")
    public ResponseEntity<String> verifyQuickNodeWebhook(@RequestParam(value = "challenge", required = false) String challenge) {
        if (challenge != null && !challenge.isEmpty()) {
            log.info("收到QuickNode webhook验证挑战: {}", challenge);
            return ResponseEntity.ok(challenge); // 返回相同的挑战字符串进行验证
        } else {
            log.info("收到QuickNode webhook端点的GET请求，无挑战参数");
            return ResponseEntity.ok("QuickNode webhook endpoint is working");
        }
    }
} 