package com.wallet.service.room.dto;

import lombok.Data;

import java.util.List;

@Data
public class RoomStatusDTO {
    private String roomId;
    private String status;
    private String tokenAddress;
    private String tokenName;
    private String tokenSymbol;
    private List<String> members;
    private Long totalTransactionVolume;
    private Double totalPnl;
}
