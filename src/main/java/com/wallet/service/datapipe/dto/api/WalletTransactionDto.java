package com.wallet.service.datapipe.dto.api;

import lombok.Data;
import java.math.BigDecimal;
import java.sql.Timestamp;

@Data
public class WalletTransactionDto {
    private String signature;
    private Timestamp transactionTime;
    private Long slot;
    private String platform;
    private String programId;
    private String tokenInputMint;
    private BigDecimal tokenInputAmount;
    private BigDecimal tokenInputPriceUsd; // Display USD price for simplicity
    private String tokenOutputMint;
    private BigDecimal tokenOutputAmount;
    private BigDecimal tokenOutputPriceUsd; // Display USD price for simplicity
    private BigDecimal totalValueUsd;
} 