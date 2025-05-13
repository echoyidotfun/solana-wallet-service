package com.wallet.service.datapipe.service;

import com.wallet.service.datapipe.dto.api.DetailedTokenTransactionDto;
import com.wallet.service.datapipe.dto.api.TokenRankingDto;
import com.wallet.service.datapipe.dto.api.WalletTransactionDto;
import com.wallet.service.datapipe.model.SmartMoneyTransaction;
import com.wallet.service.datapipe.model.Trader;
import com.wallet.service.datapipe.repository.SmartMoneyTransactionRepository;
import com.wallet.service.datapipe.repository.TraderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.function.Function;
import java.util.HashSet;

import com.wallet.service.datapipe.model.Token;
import com.wallet.service.datapipe.repository.TokenRepository;

@Service
@RequiredArgsConstructor
@Slf4j
public class SmartMoneyService {

    private final SmartMoneyTransactionRepository transactionRepository;
    private final TraderRepository traderRepository;
    private final TokenRepository tokenRepository;

    private static final String SOL_MINT_ADDRESS = "So11111111111111111111111111111111111111112";
    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int DEFAULT_RANKING_LIMIT = 10;
    private static final int DEFAULT_HOURS_LOOKBACK = 24;
    private static final int MAX_DETAILED_TRANSACTIONS_PER_TYPE = 5;

    /**
     * 1. 查询某个聪明钱地址的最近交易
     */
    public List<WalletTransactionDto> getWalletTransactions(String walletAddress, Optional<Integer> limitOpt) {
        int limit = limitOpt.orElse(DEFAULT_PAGE_SIZE);
        Pageable pageable = PageRequest.of(0, limit);
        List<SmartMoneyTransaction> transactions = transactionRepository.findByWalletAddressOrderByTransactionTimeDesc(walletAddress, pageable);
        return transactions.stream()
                .map(this::mapToWalletTransactionDto)
                .collect(Collectors.toList());
    }

    /**
     * 2. 查询近N小时内交易最频繁的代币排行
     */
    public List<TokenRankingDto> getTrendingTokens(Optional<Integer> hoursOpt, Optional<Integer> limitOpt) {
        int hours = hoursOpt.orElse(DEFAULT_HOURS_LOOKBACK);
        int limit = limitOpt.orElse(DEFAULT_RANKING_LIMIT);
        Timestamp startTime = Timestamp.from(Instant.now().minus(hours, ChronoUnit.HOURS));
        Timestamp endTime = Timestamp.from(Instant.now());
        Pageable pageable = PageRequest.of(0, limit);

        List<Object[]> rankedMints = transactionRepository.findTrendingTokensByCount(startTime, endTime, pageable);
        if (rankedMints.isEmpty()) {
            return new ArrayList<>();
        }

        List<String> topMints = rankedMints.stream().map(row -> (String) row[0]).collect(Collectors.toList());
        Map<String, Long> mintCounts = rankedMints.stream().collect(Collectors.toMap(row -> (String) row[0], row -> (Long) row[1]));

        // Fetch Token info for top mints
        Map<String, Token> tokenInfoMap = tokenRepository.findByMintAddressIn(topMints)
            .stream()
            .collect(Collectors.toMap(Token::getMintAddress, Function.identity()));

        List<SmartMoneyTransaction> relevantTransactions = transactionRepository.findByTransactionTimeBetween(startTime, endTime)
            .stream()
            .filter(tx -> (tx.getTokenInputMint() != null && topMints.contains(tx.getTokenInputMint())) || 
                         (tx.getTokenOutputMint() != null && topMints.contains(tx.getTokenOutputMint())))
            .collect(Collectors.toList());

        Set<String> walletAddresses = relevantTransactions.stream().map(SmartMoneyTransaction::getWalletAddress).collect(Collectors.toSet());
        Map<String, String> traderTags = traderRepository.findAllByWalletAddressIn(new ArrayList<>(walletAddresses))
            .stream()
            .collect(Collectors.toMap(Trader::getWalletAddress, trader -> String.join(",", trader.getTags()), (tag1, tag2) -> tag1));

        List<TokenRankingDto> result = new ArrayList<>();
        int rank = 1;
        for (String mint : topMints) {
            Token tokenInfo = tokenInfoMap.get(mint);
            if (Objects.isNull(tokenInfo)) {
                continue;
            }
            List<DetailedTokenTransactionDto> buyTxs = new ArrayList<>();
            List<DetailedTokenTransactionDto> sellTxs = new ArrayList<>();

            BigDecimal totalBuy = BigDecimal.ZERO;
            BigDecimal totalSell = BigDecimal.ZERO;
            long buyCount = 0;
            long sellCount = 0;
            Set<String> uniqueBuyersSet = new HashSet<>();
            Set<String> uniqueSellersSet = new HashSet<>();

            for (SmartMoneyTransaction tx : relevantTransactions) {
                String action = null;
                BigDecimal amount = BigDecimal.ZERO;
                BigDecimal price = BigDecimal.ZERO;
                BigDecimal value = Optional.ofNullable(tx.getTotalValueInUsd()).orElse(BigDecimal.ZERO);
                DetailedTokenTransactionDto detailedTx = null;

                if (mint.equals(tx.getTokenOutputMint())) {
                    action = "BUY";
                    amount = tx.getTokenOutputAmount();
                    price = Optional.ofNullable(tx.getTokenOutputPriceInUsd()).orElse(BigDecimal.ZERO);
                    totalBuy = totalBuy.add(Optional.ofNullable(amount).orElse(BigDecimal.ZERO));
                    buyCount++;
                    uniqueBuyersSet.add(tx.getWalletAddress());
                    detailedTx = new DetailedTokenTransactionDto(
                            tx.getSignature(),
                            tx.getTransactionTime(),
                            tx.getSlot(),
                            tx.getPlatform(),
                            action,
                            amount,
                            price,
                            value,
                            tx.getWalletAddress(),
                            traderTags.getOrDefault(tx.getWalletAddress(), "")
                    );
                    buyTxs.add(detailedTx);
                } else if (mint.equals(tx.getTokenInputMint())) {
                    action = "SELL";
                    amount = tx.getTokenInputAmount();
                    price = Optional.ofNullable(tx.getTokenInputPriceInUsd()).orElse(BigDecimal.ZERO);
                    totalSell = totalSell.add(Optional.ofNullable(amount).orElse(BigDecimal.ZERO));
                    sellCount++;
                    uniqueSellersSet.add(tx.getWalletAddress());
                    detailedTx = new DetailedTokenTransactionDto(
                            tx.getSignature(),
                            tx.getTransactionTime(),
                            tx.getSlot(),
                            tx.getPlatform(),
                            action,
                            amount,
                            price,
                            value,
                            tx.getWalletAddress(),
                            traderTags.getOrDefault(tx.getWalletAddress(), "")
                    );
                    sellTxs.add(detailedTx);
                }
            }
            
            // Sort and limit detailed transactions
            buyTxs.sort(Comparator.comparing(DetailedTokenTransactionDto::getAmount, Comparator.nullsLast(BigDecimal::compareTo)).reversed());
            sellTxs.sort(Comparator.comparing(DetailedTokenTransactionDto::getAmount, Comparator.nullsLast(BigDecimal::compareTo)).reversed());

            List<DetailedTokenTransactionDto> topBuyTxs = buyTxs.stream().limit(MAX_DETAILED_TRANSACTIONS_PER_TYPE).collect(Collectors.toList());
            List<DetailedTokenTransactionDto> topSellTxs = sellTxs.stream().limit(MAX_DETAILED_TRANSACTIONS_PER_TYPE).collect(Collectors.toList());

            TokenRankingDto dto = new TokenRankingDto();
            dto.setTokenMint(mint);
            dto.setRank(rank++);
            dto.setTransactionCount(mintCounts.getOrDefault(mint, 0L));
            dto.setBuyCount(buyCount);
            dto.setSellCount(sellCount);
            dto.setTotalBuyVolume(totalBuy);
            dto.setTotalSellVolume(totalSell);
            dto.setUniqueBuyers(uniqueBuyersSet.size());
            dto.setUniqueSellers(uniqueSellersSet.size());
            dto.setBuyTransactions(topBuyTxs);
            dto.setSellTransactions(topSellTxs);

            // Populate Token info if available
            if (tokenInfo != null) {
                dto.setName(tokenInfo.getName());
                dto.setSymbol(tokenInfo.getSymbol());
                dto.setImageUrl(tokenInfo.getImageUrl());
                dto.setDescription(tokenInfo.getDescription());
                dto.setWebsite(tokenInfo.getWebsite());
                dto.setTwitter(tokenInfo.getTwitter());
                dto.setTelegram(tokenInfo.getTelegram());
                dto.setTokenCreatedAt(tokenInfo.getCreatedAt());
                dto.setRiskScore(tokenInfo.getRiskScore());
            }

            result.add(dto);
        }

        return result;
    }

    /**
     * 3. 查询近N小时内买入最多的代币排行
     */
    public List<TokenRankingDto> getTopInflowTokens(Optional<Integer> hoursOpt, Optional<Integer> limitOpt) {
        return getTopTokens(hoursOpt, limitOpt, "BUY");
    }

    /**
     * 4. 查询近N小时内卖出最多的代币排行
     */
    public List<TokenRankingDto> getTopOutflowTokens(Optional<Integer> hoursOpt, Optional<Integer> limitOpt) {
        return getTopTokens(hoursOpt, limitOpt, "SELL");
    }

    // --- Helper Methods ---

    private WalletTransactionDto mapToWalletTransactionDto(SmartMoneyTransaction tx) {
        WalletTransactionDto dto = new WalletTransactionDto();
        dto.setSignature(tx.getSignature());
        dto.setTransactionTime(tx.getTransactionTime());
        dto.setSlot(tx.getSlot());
        dto.setPlatform(tx.getPlatform());
        dto.setProgramId(tx.getProgramId());
        dto.setTokenInputMint(tx.getTokenInputMint());
        dto.setTokenInputAmount(tx.getTokenInputAmount());
        dto.setTokenInputPriceUsd(Optional.ofNullable(tx.getTokenInputPriceInUsd()).orElse(BigDecimal.ZERO));
        dto.setTokenOutputMint(tx.getTokenOutputMint());
        dto.setTokenOutputAmount(tx.getTokenOutputAmount());
        dto.setTokenOutputPriceUsd(Optional.ofNullable(tx.getTokenOutputPriceInUsd()).orElse(BigDecimal.ZERO));
        dto.setTotalValueUsd(Optional.ofNullable(tx.getTotalValueInUsd()).orElse(BigDecimal.ZERO));
        return dto;
    }

    private List<TokenRankingDto> getTopTokens(Optional<Integer> hoursOpt, Optional<Integer> limitOpt, String type) {
        int hours = hoursOpt.orElse(DEFAULT_HOURS_LOOKBACK);
        int limit = limitOpt.orElse(DEFAULT_RANKING_LIMIT);
        Timestamp startTime = Timestamp.from(Instant.now().minus(hours, ChronoUnit.HOURS));
        Timestamp endTime = Timestamp.from(Instant.now());
        Pageable pageable = PageRequest.of(0, limit);

        List<Object[]> rankedMintsResult;
        if ("BUY".equals(type)) {
            rankedMintsResult = transactionRepository.findTopBoughtTokensByCount(startTime, endTime, pageable);
        } else { // SELL
            rankedMintsResult = transactionRepository.findTopSoldTokensByCount(startTime, endTime, pageable);
        }

        if (rankedMintsResult.isEmpty()) {
            return new ArrayList<>();
        }

        List<String> topMints = rankedMintsResult.stream().map(row -> (String) row[0]).collect(Collectors.toList());
        Map<String, Long> mintCounts = rankedMintsResult.stream().collect(Collectors.toMap(row -> (String) row[0], row -> (Long) row[1]));

        // Fetch Token info for top mints
        Map<String, Token> tokenInfoMap = tokenRepository.findByMintAddressIn(topMints)
            .stream()
            .collect(Collectors.toMap(Token::getMintAddress, Function.identity()));

        List<SmartMoneyTransaction> relevantTransactions = transactionRepository.findByTransactionTimeBetween(startTime, endTime)
            .stream()
            .filter(tx -> ("BUY".equals(type) && tx.getTokenOutputMint() != null && topMints.contains(tx.getTokenOutputMint())) || 
                         ("SELL".equals(type) && tx.getTokenInputMint() != null && topMints.contains(tx.getTokenInputMint())))
            .collect(Collectors.toList());
        
        Set<String> walletAddresses = relevantTransactions.stream().map(SmartMoneyTransaction::getWalletAddress).collect(Collectors.toSet());
        Map<String, String> traderTags = traderRepository.findAllByWalletAddressIn(new ArrayList<>(walletAddresses))
            .stream()
            .collect(Collectors.toMap(Trader::getWalletAddress, trader -> String.join(",", trader.getTags()), (tag1, tag2) -> tag1)); 


        List<TokenRankingDto> result = new ArrayList<>();
        int rank = 1;
        for (String mint : topMints) {
            Token tokenInfo = tokenInfoMap.get(mint);
            if (Objects.isNull(tokenInfo)) {
                continue;
            }
            List<DetailedTokenTransactionDto> buyTxs = new ArrayList<>();
            List<DetailedTokenTransactionDto> sellTxs = new ArrayList<>();

            BigDecimal totalVolume = BigDecimal.ZERO;
            long currentCount = mintCounts.getOrDefault(mint, 0L);
            Set<String> uniqueWalletsSet = new HashSet<>();
            
            for (SmartMoneyTransaction tx : relevantTransactions) {
                String action = null;
                BigDecimal amount = BigDecimal.ZERO;
                BigDecimal price = BigDecimal.ZERO;
                BigDecimal value = Optional.ofNullable(tx.getTotalValueInUsd()).orElse(BigDecimal.ZERO);
                DetailedTokenTransactionDto detailedTx = null;

                if ("BUY".equals(type) && mint.equals(tx.getTokenOutputMint())) {
                    action = "BUY";
                    amount = tx.getTokenOutputAmount();
                    price = Optional.ofNullable(tx.getTokenOutputPriceInUsd()).orElse(BigDecimal.ZERO);
                    totalVolume = totalVolume.add(Optional.ofNullable(amount).orElse(BigDecimal.ZERO)); 
                    uniqueWalletsSet.add(tx.getWalletAddress());
                    detailedTx = new DetailedTokenTransactionDto(
                            tx.getSignature(),
                            tx.getTransactionTime(),
                            tx.getSlot(),
                            tx.getPlatform(),
                            action,
                            amount,
                            price,
                            value,
                            tx.getWalletAddress(),
                            traderTags.getOrDefault(tx.getWalletAddress(), "")
                    );
                    buyTxs.add(detailedTx);
                } else if ("SELL".equals(type) && mint.equals(tx.getTokenInputMint())) {
                    action = "SELL";
                    amount = tx.getTokenInputAmount();
                    price = Optional.ofNullable(tx.getTokenInputPriceInUsd()).orElse(BigDecimal.ZERO);
                    totalVolume = totalVolume.add(Optional.ofNullable(amount).orElse(BigDecimal.ZERO)); 
                    uniqueWalletsSet.add(tx.getWalletAddress());
                    detailedTx = new DetailedTokenTransactionDto(
                            tx.getSignature(),
                            tx.getTransactionTime(),
                            tx.getSlot(),
                            tx.getPlatform(),
                            action,
                            amount,
                            price,
                            value,
                            tx.getWalletAddress(),
                            traderTags.getOrDefault(tx.getWalletAddress(), "")
                    );
                    sellTxs.add(detailedTx);
                }
            }
            
            // Sort and limit detailed transactions
            buyTxs.sort(Comparator.comparing(DetailedTokenTransactionDto::getAmount, Comparator.nullsLast(BigDecimal::compareTo)).reversed());
            sellTxs.sort(Comparator.comparing(DetailedTokenTransactionDto::getAmount, Comparator.nullsLast(BigDecimal::compareTo)).reversed());

            List<DetailedTokenTransactionDto> topBuyTxs = buyTxs.stream().limit(MAX_DETAILED_TRANSACTIONS_PER_TYPE).collect(Collectors.toList());
            List<DetailedTokenTransactionDto> topSellTxs = sellTxs.stream().limit(MAX_DETAILED_TRANSACTIONS_PER_TYPE).collect(Collectors.toList());

            TokenRankingDto rankingDto = new TokenRankingDto(); 
            rankingDto.setTokenMint(mint);
            rankingDto.setRank(rank++);
            rankingDto.setTransactionCount(currentCount); 
            
            if ("BUY".equals(type)) {
                rankingDto.setBuyCount(currentCount); 
                rankingDto.setSellCount(0); 
                rankingDto.setTotalBuyVolume(totalVolume); 
                rankingDto.setTotalSellVolume(BigDecimal.ZERO); 
                rankingDto.setUniqueBuyers(uniqueWalletsSet.size()); 
                rankingDto.setUniqueSellers(0); 
                rankingDto.setBuyTransactions(topBuyTxs);
                rankingDto.setSellTransactions(new ArrayList<>());
            } else { // SELL
                rankingDto.setBuyCount(0); 
                rankingDto.setSellCount(currentCount); 
                rankingDto.setTotalBuyVolume(BigDecimal.ZERO); 
                rankingDto.setTotalSellVolume(totalVolume); 
                rankingDto.setUniqueBuyers(0); 
                rankingDto.setUniqueSellers(uniqueWalletsSet.size()); 
                rankingDto.setBuyTransactions(new ArrayList<>());
                rankingDto.setSellTransactions(topSellTxs);
            }

            // Populate Token info if available
            if (tokenInfo != null) {
                rankingDto.setName(tokenInfo.getName());
                rankingDto.setSymbol(tokenInfo.getSymbol());
                rankingDto.setImageUrl(tokenInfo.getImageUrl());
                rankingDto.setDescription(tokenInfo.getDescription());
                rankingDto.setWebsite(tokenInfo.getWebsite());
                rankingDto.setTwitter(tokenInfo.getTwitter());
                rankingDto.setTelegram(tokenInfo.getTelegram());
                rankingDto.setTokenCreatedAt(tokenInfo.getCreatedAt());
                rankingDto.setRiskScore(tokenInfo.getRiskScore());
            }

            result.add(rankingDto);
        }

        return result;
    }
}
