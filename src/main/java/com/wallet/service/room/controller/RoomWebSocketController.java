package com.wallet.service.room.controller;

import com.wallet.service.room.dto.TradeEventDTO;
import com.wallet.service.room.model.SharedInfo;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.stereotype.Controller;

@Controller
public class RoomWebSocketController {

    @MessageMapping("/room/{roomId}/trade")
    @SendTo("/topic/room/{roomId}/trades")
    public TradeEventDTO broadcastTradeEvent(
            @DestinationVariable String roomId,
            TradeEventDTO tradeEvent) {
        // 处理交易事件后广播
        return tradeEvent;
    }
    
    @MessageMapping("/room/{roomId}/shared-info")
    @SendTo("/topic/room/{roomId}/shared-infos")
    public SharedInfo broadcastSharedInfo(
            @DestinationVariable String roomId,
            SharedInfo sharedInfo) {
        // 处理共享信息后广播
        return sharedInfo;
    }
}