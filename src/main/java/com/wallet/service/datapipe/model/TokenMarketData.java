package com.wallet.service.datapipe.model;

import java.math.BigDecimal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

@Entity
@Data
@Table(name = "token_market_data")
public class TokenMarketData {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(unique = true)
    private String mintAddress;  // 代币地址
    
    private String poolId;       // 主要流动池ID
    
    private String market;       // 市场类型 (raydium-clmm, jupiter, etc.)
    
    @Column(precision = 30, scale = 12)
    private BigDecimal priceQuote;  // 用报价货币表示的价格
    
    @Column(precision = 30, scale = 12)
    private BigDecimal priceUsd;    // 美元价格
    
    @Column(precision = 30, scale = 12)
    private BigDecimal liquidityQuote;  // 流动性(报价货币)
    
    @Column(precision = 38, scale = 12)
    private BigDecimal liquidityUsd;    // 流动性(USD)
    
    @Column(precision = 38, scale = 12)
    private BigDecimal marketCapQuote;  // 市值(报价货币)
    
    @Column(precision = 38, scale = 12)
    private BigDecimal marketCapUsd;    // 市值(USD)
    
    @Column(precision = 38, scale = 10)
    private BigDecimal tokenSupply;     // 代币供应量
    
    @Column(precision = 10, scale = 2)
    private Integer lpBurn;          // LP燃烧比例
    
    private String quoteToken;          // 报价代币地址
    
    private Long timestamp;             // 数据时间戳(最后更新时间)
}