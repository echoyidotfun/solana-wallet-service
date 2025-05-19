package com.wallet.service.ai.tool;

import com.wallet.service.ai.dto.AggregatedTokenDataDto;
import com.wallet.service.ai.dto.TokenAnalysisRequest;
import com.wallet.service.datapipe.model.*;
import com.wallet.service.datapipe.repository.*;

import lombok.RequiredArgsConstructor;

import org.springframework.ai.model.function.FunctionCallback;
import org.springframework.ai.model.function.FunctionCallbackWrapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Description;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.math.BigDecimal;
import java.util.Objects;

@Component
@RequiredArgsConstructor
public class TokenDatabaseTool {

    private final TokenRepository tokenRepository;
    private final TokenMarketDataRepository tokenMarketDataRepository;
    private final TokenPriceChangeRepository tokenPriceChangeRepository;
    private final TokenTopHoldersRepository tokenTopHoldersRepository;
    private final TokenTransactionStatsRepository tokenTransactionStatsRepository;
    private final TokenTrendingRankingRepository tokenTrendingRankingRepository;

    private boolean isLikelyContractAddress(String identifier) {
        return identifier != null && identifier.length() >= 32 && identifier.length() <= 44 && identifier.matches("^[1-9A-HJ-NP-Za-km-z]+$");
    }

    public AggregatedTokenDataDto getTokenAnalysisData(TokenAnalysisRequest request) {
        String tokenIdentifier = request.tokenIdentifier();
        System.out.println("[TokenDatabaseTool] Received request for token: " + tokenIdentifier);

        if (tokenIdentifier == null || tokenIdentifier.isBlank()) {
            System.out.println("[TokenDatabaseTool] Token identifier is blank.");
            return null;
        }

        Optional<Token> tokenOptional;
        if (isLikelyContractAddress(tokenIdentifier)) {
            System.out.println("[TokenDatabaseTool] Identifier '" + tokenIdentifier + "' seems to be a contract address.");
            tokenOptional = tokenRepository.findByMintAddress(tokenIdentifier);
        } else {
            System.out.println("[TokenDatabaseTool] Identifier '" + tokenIdentifier + "' seems to be a symbol.");
            List<Token> potentialTokens = tokenRepository.findBySymbol(tokenIdentifier);

            if (potentialTokens.isEmpty()) {
                System.out.println("[TokenDatabaseTool] No tokens found for symbol: " + tokenIdentifier);
                tokenOptional = Optional.empty();
            } else if (potentialTokens.size() == 1) {
                tokenOptional = Optional.of(potentialTokens.get(0));
            } else {
                System.out.println("[TokenDatabaseTool] Multiple tokens found for symbol: " + tokenIdentifier + ". Ranking by market cap.");
                
                Map<String, BigDecimal> marketCaps = new HashMap<>();
                for (Token token : potentialTokens) {
                    Optional<TokenMarketData> md = tokenMarketDataRepository.findByMintAddress(token.getMintAddress());
                    BigDecimal marketCap = md.map(TokenMarketData::getMarketCapUsd)
                                             .filter(Objects::nonNull)
                                             .orElse(null); // Use null for missing or explicitly null market cap
                    marketCaps.put(token.getMintAddress(), marketCap);
                }

                potentialTokens.sort((t1, t2) -> {
                    BigDecimal mc1 = marketCaps.get(t1.getMintAddress());
                    BigDecimal mc2 = marketCaps.get(t2.getMintAddress());
                    if (mc1 == null && mc2 == null) return 0;
                    if (mc1 == null) return 1; // nulls (missing market cap) sorted last
                    if (mc2 == null) return -1; // nulls (missing market cap) sorted last
                    return mc2.compareTo(mc1); // Sort descending by market cap
                });
                
                tokenOptional = Optional.of(potentialTokens.get(0)); // Take the top one
                System.out.println("[TokenDatabaseTool] Selected token " + tokenOptional.get().getName() + 
                                   " with mint " + tokenOptional.get().getMintAddress() + " as highest market cap.");
                // NOTE: User requested top two. Current implementation selects the top one due to
                // the single AggregatedTokenDataDto return type. This can be revisited.
            }
        }

        if (tokenOptional.isEmpty()) {
            System.out.println("[TokenDatabaseTool] Token not found or could not be determined for identifier: " + tokenIdentifier);
            return null;
        }

        Token tokenInfo = tokenOptional.get();
        String tokenMintAddress = tokenInfo.getMintAddress();

        System.out.println("[TokenDatabaseTool] Found token: " + tokenInfo.getName() + " (Mint: " + tokenMintAddress + ")");

        TokenMarketData marketData = tokenMarketDataRepository.findByMintAddress(tokenMintAddress)
                .orElse(new TokenMarketData());
        
        TokenPriceChange priceChanges = tokenPriceChangeRepository.findByMintAddressAndTimeframe(tokenMintAddress, "24h")
                .orElse(new TokenPriceChange());

        TokenTopHolders topHolders = tokenTopHoldersRepository.findByMintAddress(tokenMintAddress).orElse(new TokenTopHolders());
        
        TokenTransactionStats transactionStats = tokenTransactionStatsRepository.findByMintAddress(tokenMintAddress)
            .orElse(new TokenTransactionStats());

        TokenTrendingRanking trendingRank = tokenTrendingRankingRepository.findByMintAddress(tokenMintAddress)
            .orElse(new TokenTrendingRanking());

        return new AggregatedTokenDataDto(tokenInfo, marketData, priceChanges, null, transactionStats, trendingRank, topHolders);
    }
}

@Configuration
class TokenToolConfiguration {

    @Bean
    @Description("Retrieves comprehensive aggregated market and metadata for a given cryptocurrency token. Use this to get a full picture of a token's current standing, including its basic info, market stats, price changes, top holders, transaction volume, and trending rank. Excludes smart money transaction details for now.")
    public FunctionCallback tokenAnalysisToolFunction(TokenDatabaseTool tokenDatabaseTool) {
        return FunctionCallbackWrapper.builder(tokenDatabaseTool::getTokenAnalysisData)
            .withName("getTokenAnalysisData")
            .withDescription("Fetches aggregated on-chain and market data (excluding specific smart money transactions) for a specific cryptocurrency token identifier. The identifier is provided as part of an object. It can be a token symbol (e.g., SOL) or a contract address.")
            .withInputType(TokenAnalysisRequest.class)
            .build();
    }
} 