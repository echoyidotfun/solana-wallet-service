package com.wallet.service.room.model;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Data
@Table(name = "room_members")
public class RoomMember {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @ManyToOne
    @JoinColumn(name = "room_id")
    private TradeRoom room;
    
    private String walletAddress;  // 成员钱包地址
    
    @CreationTimestamp
    private LocalDateTime joinTime;  // 加入时间
    
    private LocalDateTime lastActiveTime;  // 最后活跃时间
    
    private LocalDateTime leaveTime;  // 离开时间
    
    private boolean active = true;  // 是否活跃
}