package com.wallet.service.datapipe.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

@Entity
@Data
@Table(name = "token_top_holders")
public class TokenTopHolders {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(unique = true)
    private String mintAddress;  // 代币地址
    
    @Column(columnDefinition = "TEXT")
    private String holdersJson;  // 持有者JSON数据，包含top持有者列表
    
    private Long timestamp;      // 数据时间戳(最后更新时间)
}