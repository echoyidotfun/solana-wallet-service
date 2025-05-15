package com.wallet.service.datapipe.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class AnalyzedWalletAction {
    private String transactionSignature;
    private String walletAddress; // 执行此操作的钱包地址

    // 钱包付出的代币 (交易的输入方)
    private String tokenInMint;
    private BigDecimal tokenInAmount; // 金额应为正数

    // 钱包收到的代币 (交易的输出方)
    private String tokenOutMint;
    private BigDecimal tokenOutAmount; // 金额应为正数

    private String platform; // 例如：Jupiter, Raydium, Pump.fun
    private String programId; // 驱动此操作的主要程序ID
    private Long timestamp; // 交易区块时间
    private Long slot;
} 