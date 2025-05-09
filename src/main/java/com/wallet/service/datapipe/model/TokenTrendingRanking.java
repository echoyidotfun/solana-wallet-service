package com.wallet.service.datapipe.model;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

@Entity
@Data
@Table(name = "token_trending_rankings")
public class TokenTrendingRanking {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    private String mintAddress;
    
    private String timeframe;
    
    private Integer ranking;
    
    private Long batchTimestamp;
}

