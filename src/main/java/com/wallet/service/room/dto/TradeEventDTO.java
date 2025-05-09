package com.wallet.service.room.dto;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class TradeEventDTO {
    private String roomId;
    private String walletAddress;
    private String tokenInAddress;
    private String tokenOutAddress;
    private String transactionHash;
    private String transactionType; // BUY, SELL
    private BigDecimal amount;
    private BigDecimal price;
    private LocalDateTime timestamp;
}