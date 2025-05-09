package com.wallet.service.datapipe.dto;

import lombok.Data;
import java.math.BigDecimal;

/**
 * 统一的代币信息响应对象，包含所有需要返回给前端的字段
 */
@Data
public class TokenResponseDto {
    // 基本信息
    private String mintAddress;
    private String name;
    private String symbol;
    private Integer decimals;
    private String imageUrl;
    private String description;
    
    // 社媒信息
    private String website;
    private String twitter;
    private String telegram;
    private String createdOn;
    private Long createdAt;
    
    // 市场数据
    private String poolId;
    private String market;
    private BigDecimal priceQuote;
    private BigDecimal priceUsd;
    private BigDecimal liquidityQuote;
    private BigDecimal liquidityUsd;
    private BigDecimal marketCapQuote;
    private BigDecimal marketCapUsd;
    private BigDecimal tokenSupply;
    
    // 交易数据
    private Integer buys;
    private Integer sells;
    private Integer totalTxns;
    private BigDecimal volume;
    
    // 价格变化数据
    private BigDecimal priceChange1h;
    private BigDecimal priceChange4h;
    private BigDecimal priceChange24h;
    private BigDecimal priceChange7d;
    
    // 风险信息
    private Boolean rugged;
    private Integer riskScore;
    private String riskItems; // JSON格式字符串
}