package com.wallet.service.datapipe.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Entity
@Data
@NoArgsConstructor
@Table(name = "traders")
public class Trader {

    @Id
    @Column(length = 128)
    private String walletAddress;

    private BigDecimal realizedPnl;
    private BigDecimal unrealizedPnl;
    private BigDecimal totalPnl;
    private BigDecimal totalInvested;
    private Integer totalWins;
    private Integer totalLosses;
    private Double winPercentage;
    private BigDecimal averageBuyAmount;
    private Integer sniperScore; // 狙击手分数

    @Column(columnDefinition = "TEXT")
    private String tags;

    private Long lastUpdatedAt; // Timestamp of the last update

    public Trader(String walletAddress) {
        this.walletAddress = walletAddress;
    }
}
