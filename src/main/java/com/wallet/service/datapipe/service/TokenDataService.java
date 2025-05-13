package com.wallet.service.datapipe.service;

import com.wallet.service.datapipe.dto.TokenInfoDto;
import com.wallet.service.datapipe.model.Token;
import com.wallet.service.datapipe.model.TokenMarketData;
import com.wallet.service.datapipe.model.TokenPriceChange;
import com.wallet.service.datapipe.model.TokenTransactionStats;
import com.wallet.service.datapipe.model.TokenTrendingRanking;
import com.wallet.service.datapipe.repository.TokenMarketDataRepository;
import com.wallet.service.datapipe.repository.TokenPriceChangeRepository;
import com.wallet.service.datapipe.repository.TokenRepository;
import com.wallet.service.datapipe.repository.TokenTransactionStatsRepository;
import com.wallet.service.datapipe.repository.TokenTrendingRankingRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 代币数据服务，提供各种代币数据查询功能
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class TokenDataService {
    
    private final TokenRepository tokenRepository;
    private final TokenMarketDataRepository tokenMarketDataRepository;
    private final TokenPriceChangeRepository tokenPriceChangeRepository;
    private final TokenTransactionStatsRepository tokenTransactionStatsRepository;
    private final TokenTrendingRankingRepository trendingRepository;
    private final SolanaTrackerService solanaTrackerService;
    private final DataSyncService dataSyncService;
    
    /**
     * 获取热点代币列表
     *
     * @param timeframe 时间范围
     * @param limit 返回数量
     * @return 热点代币列表
     */
    public List<Map<String, Object>> getTrendingTokens(String timeframe, int limit) {
        List<TokenTrendingRanking> trendingRankings = 
            trendingRepository.findByTimeframeOrderByRankingAsc(timeframe);
        
        List<Map<String, Object>> result = new ArrayList<>();
        
        for (TokenTrendingRanking ranking : trendingRankings) {            
            String mintAddress = ranking.getMintAddress();
            Map<String, Object> tokenInfo = getTokenInfo(mintAddress);
            
            if (tokenInfo != null) {                
                result.add(tokenInfo);
            }
        }
        
        return result;
    }
    
    /**
     * 获取交易量排行榜
     *
     * @param timeframe 时间范围
     * @param limit 返回数量
     * @return 交易量排行榜
     */
    public List<Map<String, Object>> getVolumeTokens(String timeframe, int limit) {
        List<TokenTransactionStats> volumeStats = 
            tokenTransactionStatsRepository.findTopByVolumeDesc(PageRequest.of(0, limit));
        
        List<Map<String, Object>> result = new ArrayList<>();
        
        for (TokenTransactionStats stat : volumeStats) {
            String mintAddress = stat.getMintAddress();
            Map<String, Object> tokenInfo = getTokenInfo(mintAddress);
            
            if (tokenInfo != null) {
                result.add(tokenInfo);
            }
        }
        return result;
    }
    
    /**
     * 获取最新代币列表
     *
     * @param limit 返回数量
     * @return 最新代币列表
     */
    public List<Map<String, Object>> getLatestTokens(int limit) {
        Pageable pageable = PageRequest.of(0, limit);
        List<Token> tokens = tokenRepository.findAllByOrderByCreatedAtDesc(pageable);
        
        List<Map<String, Object>> result = new ArrayList<>();
        
        for (Token token : tokens) {
            String mintAddress = token.getMintAddress();
            Map<String, Object> tokenInfo = getTokenInfo(mintAddress);
            
            if (tokenInfo != null) {
                result.add(tokenInfo);
            }
        }
        
        return result;
    }
    
    /**
     * 获取代币详细信息
     *
     * @param mintAddress 代币地址
     * @return 代币详细信息
     */
    public Map<String, Object> getTokenInfo(String mintAddress) {
        Optional<Token> tokenOpt = tokenRepository.findByMintAddress(mintAddress);
        
        // 数据库中没有找到该代币信息，从SolanaTracker API获取
        if (tokenOpt.isEmpty()) {
            log.info("数据库中未找到代币信息: {}, 尝试从外部API获取", mintAddress);
            return getTokenInfoFromExternalApi(mintAddress);
        }
        
        Token token = tokenOpt.get();
        Optional<TokenMarketData> marketDataOpt = tokenMarketDataRepository.findByMintAddress(mintAddress);
        Optional<TokenTransactionStats> statsOpt = tokenTransactionStatsRepository.findByMintAddress(mintAddress);
        
        Map<String, Object> response = new HashMap<>();
        
        // 基本信息
        response.put("mintAddress", token.getMintAddress());
        response.put("name", token.getName());
        response.put("symbol", token.getSymbol());
        response.put("decimals", token.getDecimals());
        response.put("imageUrl", token.getImageUrl());
        response.put("description", token.getDescription());
        response.put("website", token.getWebsite());
        response.put("twitter", token.getTwitter());
        response.put("telegram", token.getTelegram());
        response.put("createdOn", token.getCreatedOn());
        response.put("createdAt", token.getCreatedAt());
        response.put("deployer", token.getDeployer());
        
        // 风险评估
        response.put("isRugged", token.getIsRugged());
        response.put("riskScore", token.getRiskScore());
        response.put("riskItems", token.getRiskItems());
        
        // 市场数据
        if (marketDataOpt.isPresent()) {
            TokenMarketData marketData = marketDataOpt.get();
            Map<String, Object> marketInfo = new HashMap<>();
            
            marketInfo.put("priceQuote", marketData.getPriceQuote());
            marketInfo.put("priceUsd", marketData.getPriceUsd());
            marketInfo.put("liquidityQuote", marketData.getLiquidityQuote());
            marketInfo.put("liquidityUsd", marketData.getLiquidityUsd());
            marketInfo.put("marketCapQuote", marketData.getMarketCapQuote());
            marketInfo.put("marketCapUsd", marketData.getMarketCapUsd());
            marketInfo.put("tokenSupply", marketData.getTokenSupply());
            marketInfo.put("lpBurn", marketData.getLpBurn());
            marketInfo.put("poolId", marketData.getPoolId());
            marketInfo.put("market", marketData.getMarket());
            marketInfo.put("quoteToken", marketData.getQuoteToken());
            
            response.put("marketData", marketInfo);
        }
        
        // 交易统计
        if (statsOpt.isPresent()) {
            TokenTransactionStats stats = statsOpt.get();
            Map<String, Object> txStats = new HashMap<>();
            
            txStats.put("buys", stats.getBuys());
            txStats.put("sells", stats.getSells());
            txStats.put("total", stats.getTotalTxns());
            txStats.put("volume", stats.getVolume());
            
            response.put("transactions", txStats);
        }
        
        // 价格变化
        List<TokenPriceChange> priceChanges = 
            tokenPriceChangeRepository.findByMintAddress(mintAddress);
        
        if (!priceChanges.isEmpty()) {
            Map<String, Object> changes = new HashMap<>();
            
            for (TokenPriceChange change : priceChanges) {
                changes.put(change.getTimeframe(), change.getPriceChangePercentage());
            }
            
            response.put("priceChanges", changes);
        }
        
        return response;
    }
    
    /**
     * 从外部API获取代币信息
     *
     * @param mintAddress 代币地址
     * @return 代币详细信息
     */
    private Map<String, Object> getTokenInfoFromExternalApi(String mintAddress) {
        try {
            TokenInfoDto tokenInfo = solanaTrackerService.getTokenInfo(mintAddress);
            
            if (tokenInfo == null) {
                log.warn("外部API未找到代币信息: {}", mintAddress);
                return null;
            }
            
            // 保存获取到的数据到数据库
            dataSyncService.saveTokenData(mintAddress, tokenInfo);
            
            // 递归调用本地数据库获取方法
            return getTokenInfo(mintAddress);
            
        } catch (Exception e) {
            log.error("从外部API获取代币信息失败: {}", mintAddress, e);
            return null;
        }
    }
} 