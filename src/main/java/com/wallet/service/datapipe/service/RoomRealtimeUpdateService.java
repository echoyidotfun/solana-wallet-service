package com.wallet.service.datapipe.service;

import com.wallet.service.datapipe.dto.AnalyzedWalletAction;

public interface RoomRealtimeUpdateService {

    /**
     * 将分析后的钱包操作推送到指定房间的STOMP主题。
     *
     * @param roomId 房间ID
     * @param action 分析后的钱包操作
     */
    void sendActionUpdate(String roomId, AnalyzedWalletAction action);
} 