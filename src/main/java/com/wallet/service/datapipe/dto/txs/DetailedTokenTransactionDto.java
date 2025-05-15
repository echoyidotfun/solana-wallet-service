package com.wallet.service.datapipe.dto.txs;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.sql.Timestamp;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DetailedTokenTransactionDto {
    private String signature;
    private Timestamp transactionTime;
    private Long slot;
    private String platform;
    private String action; // "Buy" or "Sell"
    private BigDecimal amount; // Amount of the ranked token bought/sold
    private BigDecimal priceUsd; // Price of the ranked token in USD at time of tx
    private BigDecimal totalValueUsd; // Total value of this specific buy/sell in USD
    private String walletAddress;
    private String walletTag; // Tag from Trader table
} 