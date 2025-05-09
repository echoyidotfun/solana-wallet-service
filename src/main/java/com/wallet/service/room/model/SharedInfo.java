package com.wallet.service.room.model;

import java.time.LocalDateTime;

import org.hibernate.annotations.CreationTimestamp;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Data;

@Entity
@Data
@Table(name = "shared_infos")
public class SharedInfo {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @ManyToOne
    @JoinColumn(name = "room_id")
    private TradeRoom room;
    
    private String sharerWallet;  // 分享者钱包地址
    
    private String contentUrl;  // 分享内容URL
    
    @Enumerated(EnumType.STRING)
    private InfoType infoType;  // 信息类型
    
    private String contentMetadata;  // 内容元数据JSON
    
    @CreationTimestamp
    private LocalDateTime shareTime;  // 分享时间
    
    // 信息类型枚举
    public enum InfoType {
        TWITTER,
        TELEGRAM,
        DISCORD,
        INSTAGRAM,
        OTHER
    }
}
