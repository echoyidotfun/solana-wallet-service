package com.wallet.service.datapipe.model;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Data;

/**
 * 聪明钱交易数据实体 (新版)
 * 存储被跟踪的聪明钱钱包的交易信息，区分输入输出Token
 */
@Entity
@Table(name = "smart_money_transactions", 
       indexes = {
           @Index(name = "idx_wallet_address", columnList = "walletAddress"),
           @Index(name = "idx_transaction_time", columnList = "transactionTime"),
           @Index(name = "idx_platform", columnList = "platform"),
           @Index(name = "idx_token_input_mint", columnList = "tokenInputMint"),
           @Index(name = "idx_token_output_mint", columnList = "tokenOutputMint"),
           @Index(name = "idx_slot", columnList = "slot") // 添加slot索引
       })
@Data
public class SmartMoneyTransaction {
    
    @Id
    @Column(length = 88) // Base58 编码的签名长度通常小于88
    private String signature;
    
    @Column(nullable = false, length = 44) // Base58 编码的地址长度通常是44
    private String walletAddress; // 聪明钱钱包地址 (Signer/Tracked Address)
    
    @Column(nullable = false)
    private Timestamp transactionTime; // 交易发生时间 (blockTime)
    
    @Column(nullable = true)
    private Long slot; // 交易发生的slot
    
    @Column(length = 30) // 例如 "Jupiter", "Pump", "Raydium"
    private String platform; 
    
    @Column(length = 44)
    private String programId; // 交互的Program ID
    
    // 输入Token信息
    @Column(length = 44)
    private String tokenInputMint;
    
    @Column(precision = 30, scale = 18) // 调整精度和标度以适应常见代币
    private BigDecimal tokenInputAmount;
    
    // 输出Token信息
    @Column(length = 44)
    private String tokenOutputMint;
    
    @Column(precision = 30, scale = 18) // 调整精度和标度
    private BigDecimal tokenOutputAmount;
    
    // 价格和价值信息 (待计算填充)
    @Column(precision = 30, scale = 18) 
    private BigDecimal tokenInputPriceInSol; // (可选) 输入Token的SOL价格
    
    @Column(precision = 30, scale = 18)
    private BigDecimal tokenOutputPriceInSol; // (可选) 输出Token的SOL价格
    
    @Column(precision = 30, scale = 18)
    private BigDecimal tokenInputPriceInUsd; // (可选) 输入Token的USD价格
    
    @Column(precision = 30, scale = 18)
    private BigDecimal tokenOutputPriceInUsd; // (可选) 输出Token的USD价格

    @Column(precision = 30, scale = 18)
    private BigDecimal totalValueInSol; // (可选) 交易总价值 (SOL计价)
    
    @Column(precision = 30, scale = 18)
    private BigDecimal totalValueInUsd; // (可选) 交易总价值 (USD计价)
    
    // 创建时间
    @Column(nullable = false, updatable = false)
    private Timestamp createdAt;
    
    // 预创建回调
    @PrePersist
    protected void onCreate() {
        Timestamp now = Timestamp.from(Instant.now());
        createdAt = now;
        if (transactionTime == null) {
            transactionTime = now;
        }
    }
} 