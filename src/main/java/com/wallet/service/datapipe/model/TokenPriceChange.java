package com.wallet.service.datapipe.model;

import java.math.BigDecimal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Data;

@Entity
@Data
@Table(name = "token_price_changes", 
       uniqueConstraints = {@UniqueConstraint(columnNames = {"mintAddress", "timeframe"})})
public class TokenPriceChange {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    private String mintAddress;  // 代币地址
    
    private String timeframe;    // 时间范围(1m,5m,15m,30m,1h,4h,12h,24h)
    
    @Column(precision = 30, scale = 10)
    private BigDecimal priceChangePercentage;  // 价格变化百分比
    
    private Long timestamp;      // 数据时间戳(最后更新时间)
}