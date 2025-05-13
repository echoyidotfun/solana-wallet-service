package com.wallet.service.datapipe.dto;

import lombok.Data;

/**
 * 交易分析结果结构
 */
@Data
public class ParsedTransactionDto {
    private String signature;
    private Long timestamp;
    private String platform; // Jupiter/Pump/Raydium
    private String programId;
    private String signerAddress;
    private Long slot;
    private String tokenInputMint;
    private Double tokenInputAmount;
    private String tokenOutputMint;
    private Double tokenOutputAmount;
}