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
@Table(name = "tokens")
public class Token {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(unique = true)
    private String mintAddress;  // 代币地址
    
    private String name;         // 代币名称
    
    private String symbol;       // 代币符号
    
    private Integer decimals;    // 精度
    
    private String imageUrl;     // 图片URL
    
    @Column(columnDefinition = "TEXT")
    private String description;  // 描述
    
    private String website;      // 网站
    
    private String twitter;      // Twitter链接
    
    private String telegram;     // Telegram链接
    
    private String createdOn;    // 发射平台
    
    private String deployer;     // 发布者地址
    
    private Long createdAt;      // 创建时间戳
    
    private Boolean isRugged = false;  // 是否跑路
    
    private Integer riskScore;   // 风险评分
    
    @Column(columnDefinition = "TEXT")
    private String riskItems;    // 风险项JSON
    
    private Long timestamp;      // 数据时间戳(最后更新时间)
}