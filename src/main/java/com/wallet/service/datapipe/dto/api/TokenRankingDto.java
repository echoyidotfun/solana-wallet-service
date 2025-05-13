package com.wallet.service.datapipe.dto.api;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TokenRankingDto {
    private String tokenMint;
    private int rank;
    private long transactionCount; // Total transactions involving this token (buy or sell)
    private long buyCount; // Number of times this token was bought
    private long sellCount; // Number of times this token was sold
    private BigDecimal totalBuyVolume; // Total amount of this token bought by smart money
    private BigDecimal totalSellVolume; // Total amount of this token sold by smart money
    private long uniqueBuyers; // Number of unique buyers
    private long uniqueSellers; // Number of unique sellers

    // Token Info from Token entity
    private String name;         
    private String symbol;       
    private String imageUrl;     
    private String description;  
    private String website;      
    private String twitter;      
    private String telegram;     
    private Long tokenCreatedAt; // Token creation timestamp
    private Integer riskScore;   

    private List<DetailedTokenTransactionDto> buyTransactions;
    private List<DetailedTokenTransactionDto> sellTransactions; 
} 