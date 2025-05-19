package com.wallet.service.ai.dto;

import com.wallet.service.datapipe.model.Token;
import com.wallet.service.datapipe.model.TokenMarketData;
import com.wallet.service.datapipe.model.TokenPriceChange;
import com.wallet.service.datapipe.model.TokenTopHolders;
import com.wallet.service.datapipe.model.TokenTransactionStats;
import com.wallet.service.datapipe.model.TokenTrendingRanking;
// Assuming TokenTopHolders might also be relevant, can be added later if needed.

public record AggregatedTokenDataDto(
    Token tokenInfo,
    TokenMarketData marketData,
    TokenPriceChange priceChanges,
    SmartMoneyActivityDto smartMoneyActivity,
    TokenTransactionStats transactionStats,
    TokenTrendingRanking trendingRank,
    TokenTopHolders topHolders
) {} 