package com.wallet.service.room.model;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.Data;

@Entity
@Data
@Table(name = "trade_rooms")
public class TradeRoom {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    private String roomId;  // UUID格式的房间ID
    
    private String creatorWallet;  // 创建者钱包地址
    
    @Enumerated(EnumType.STRING)
    private RoomType roomType = RoomType.COLLABORATIVE;  // 房间类型，默认协作模式
    
    private String tokenAddress;  // 代币地址
    
    private String tokenSymbol;   // 代币符号
    
    private String tokenName;     // 代币名称
    
    private Integer recycleHours = 12;  // 自动回收时间（小时）
    
    private String password;      // 可选的房间密码
    
    @Enumerated(EnumType.STRING)
    private RoomStatus status = RoomStatus.CREATING;  // 房间状态
    
    @CreationTimestamp
    private LocalDateTime createTime;  // 创建时间
    
    @UpdateTimestamp
    private LocalDateTime updateTime;  // 更新时间
    
    private LocalDateTime lastActiveTime;  // 最后活跃时间
    
    @OneToMany(mappedBy = "room", cascade = CascadeType.ALL)
    private Set<RoomMember> members = new HashSet<>();
    
    // 房间类型枚举
    public enum RoomType {
        COLLABORATIVE,  // 协作模式
        COPY_TRADING    // 跟单模式
    }
    
    // 房间状态枚举
    public enum RoomStatus {
        CREATING,  // 创建中
        OPEN,      // 开放中
        CLOSED     // 已关闭
    }
}