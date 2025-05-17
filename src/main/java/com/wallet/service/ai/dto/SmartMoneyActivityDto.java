package com.wallet.service.ai.dto;

import java.math.BigDecimal;
import java.util.List;

// Using records for concise DTOs, requires Java 16+
// If using an older Java version, you would create a traditional class with getters, setters, constructor, etc.

public record SmartMoneyActivityDto(
    List<SmartMoneyTransactionDto> recentTransactions,
    BigDecimal netFlow24hUSD,
    Integer activeSmartWallets24h
) {
    // Inner record for individual transaction summary, if needed, or use your existing SmartMoneyTransaction model
    // For now, let's assume a simplified version or that you might map your existing SmartMoneyTransaction
    public record SmartMoneyTransactionDto(
        String type, // e.g., "buy", "sell"
        BigDecimal amountUSD,
        String walletAddress, // Can be formatted short
        Long timestamp,
        String platform // Optional: platform where transaction occurred
    ) {}
} 