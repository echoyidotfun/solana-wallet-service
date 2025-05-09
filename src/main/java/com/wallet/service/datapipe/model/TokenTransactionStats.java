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
@Table(name = "token_transaction_stats")
public class TokenTransactionStats {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(unique = true)
    private String mintAddress;  // 代币地址
    
    private Integer buys;        // 买入交易数
    
    private Integer sells;       // 卖出交易数
    
    private Integer totalTxns;   // 总交易数
    
    @Column(precision = 30, scale = 10)
    private BigDecimal volume;   // 交易量
    
    private Long timestamp;      // 数据时间戳(最后更新时间)
}