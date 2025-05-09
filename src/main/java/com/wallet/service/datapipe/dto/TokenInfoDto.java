package com.wallet.service.datapipe.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 代币信息数据传输对象，用于统一处理从SolanaTracker API获取的代币数据
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class TokenInfoDto {
    // 基本信息
    private TokenMetaDto token;
    // 池子信息
    private List<PoolInfoDto> pools;
    // 价格变化信息
    private Map<String, PriceChangeDto> events;
    // 风险信息
    private RiskInfoDto risk;
    
    /**
     * 代币元数据
     */
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class TokenMetaDto {
        private String name;
        private String symbol;
        private String mint;
        private String uri;
        private Integer decimals;
        private Boolean hasFileMetaData;
        private String createdOn;
        private String description;
        private String image;
        private Boolean showName;
        private String website;
        private String twitter;
        private String telegram;
        private Object strictSocials;
        private Boolean isMutable;
        private Boolean jupiterVerified;
    }
    
    /**
     * 池子信息
     */
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class PoolInfoDto {
        private String poolId;
        private MonetaryValueDto liquidity;
        private MonetaryValueDto price;
        private BigDecimal tokenSupply;
        private Integer lpBurn;
        private String tokenAddress;
        private MonetaryValueDto marketCap;
        private String market;
        private String quoteToken;
        private Integer decimals;
        private SecurityDto security;
        private String deployer;
        private Long lastUpdated;
        private Long createdAt;
        private TransactionDto txns;
    }
    
    /**
     * 价格变化数据
     */
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class PriceChangeDto {
        private BigDecimal priceChangePercentage;
    }
    
    /**
     * 风险信息
     */
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class RiskInfoDto {
        private Boolean rugged;
        private Integer score;
        private List<RiskItemDto> risks;
        private Boolean jupiterVerified;
    }
    
    /**
     * 风险项
     */
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class RiskItemDto {
        private String name;
        private String description;
        private String level;
        private Integer score;
    }
    
    /**
     * 交易数据
     */
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class TransactionDto {
        private Integer buys;
        private Integer sells;
        private Integer total;
        private BigDecimal volume;
    }
    
    /**
     * 安全信息
     */
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class SecurityDto {
        private String freezeAuthority;
        private String mintAuthority;
    }
    
    /**
     * 货币价值
     */
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class MonetaryValueDto {
        private BigDecimal quote;
        private BigDecimal usd;
    }
}
