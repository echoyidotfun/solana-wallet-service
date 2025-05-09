package com.wallet.service.datapipe.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

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

/**
 * 代币数据同步服务，定时从数据源获取并更新数据库
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class TokenDataSyncService {
    
    private final SolanaTrackerService solanaTrackerService;
    private final TokenRepository tokenRepository;
    private final TokenMarketDataRepository tokenMarketDataRepository;
    private final TokenPriceChangeRepository tokenPriceChangeRepository;
    private final TokenTransactionStatsRepository tokenTransactionStatsRepository;
    private final TokenTrendingRankingRepository tokenTrendingRankingRepository;
    private final TransactionTemplate transactionTemplate;
    
    // 热点趋势代币时间范围
    private static final String[] TREND_TIMEFRAMES = {"1h", "24h"};
    
    // 文本字段最大长度限制
    private static final int MAX_NORMAL_STRING_LENGTH = 255; // 普通VARCHAR字段的最大长度
    
    // API调用间隔(毫秒)，防止限流
    @Value("${app.scheduler.api-call-interval:2000}")
    private long apiCallInterval;
    
    /**
     * 统一数据同步任务
     * 串行执行三个子任务，子任务之间添加延迟，避免API限流
     */
    @Scheduled(fixedRateString = "${app.scheduler.unified-sync.interval:30000}")
    public void unifiedSyncTask() {
        log.info(">>>>>>开始统一数据同步任务<<<<<<");
        
        try {
            // 1. 同步热点趋势代币
            syncTrendingTokensInternal();
            
            // 等待一定时间，避免API限流
            sleep(apiCallInterval);
            
            // 2. 同步交易量代币
            syncVolumeTokensInternal();
            
            // 等待一定时间，避免API限流
            sleep(apiCallInterval);
            
            // 3. 同步最新代币
            syncLatestTokensInternal();
            
            log.info(">>>>>>统一数据同步任务完成<<<<<<");
        } catch (Exception e) {
            log.error("!!!!!!统一数据同步任务执行失败!!!!!!", e);
        }
    }
    
    /**
     * 同步热点趋势代币数据 (内部方法)
     */
    @Transactional
    public void syncTrendingTokensInternal() {
        for (String timeframe : TREND_TIMEFRAMES) {
            log.info("同步{}热点趋势代币", timeframe);
            try {
                List<TokenInfoDto> trendingTokens = solanaTrackerService.getTrendingTokens(timeframe);
                
                if (trendingTokens.isEmpty()) {
                    log.warn("获取到的{}热点趋势代币为空列表，可能是API限流或其他错误", timeframe);
                    continue;
                }
                                
                for (int i = 0; i < trendingTokens.size(); i++) {
                    TokenInfoDto tokenInfo = trendingTokens.get(i);
                    String mintAddress = tokenInfo.getToken().getMint();
                    
                    try {
                        // 保存代币基本信息和相关数据
                        saveTokenData(mintAddress, tokenInfo);
                    } catch (Exception e) {
                        log.error("保存代币数据失败: {}", mintAddress, e);
                    }
                }

                // 使用独立事务更新榜单
                try {
                    updateTrendingRanking(timeframe, trendingTokens);
                } catch (Exception e) {
                    log.error("更新{}热点趋势榜单失败", timeframe, e);
                }
                
                // 如果有多个timeframe，添加小暂停
                if (timeframe.equals(TREND_TIMEFRAMES[0]) && TREND_TIMEFRAMES.length > 1) {
                    sleep(apiCallInterval);
                }
                
                log.info("同步{}热点趋势代币完成", timeframe);
            } catch (Exception e) {
                log.error("同步{}热点趋势代币发生错误", timeframe, e);
            }
        }
    }
    
    /**
     * 同步交易量最大的代币 (内部方法)
     */
    @Transactional
    public void syncVolumeTokensInternal() {
        for (String timeframe : TREND_TIMEFRAMES) {
            log.info("同步{}交易量最大的代币", timeframe);
            try {
                List<TokenInfoDto> volumeTokens = solanaTrackerService.getVolumeTokens(timeframe);
                
                if (volumeTokens.isEmpty()) {
                    log.warn("获取到的交易量代币为空列表，可能是API限流或其他错误");
                    return;
                }
                
                for (int i = 0; i < volumeTokens.size(); i++) {
                    TokenInfoDto tokenInfo = volumeTokens.get(i);
                    String mintAddress = tokenInfo.getToken().getMint();
                    
                    try {
                        // 保存代币基本信息和相关数据
                        saveTokenData(mintAddress, tokenInfo);
                    } catch (Exception e) {
                        log.error("保存代币数据失败: {}", mintAddress, e);
                    }
                }
                
                log.info("同步交易量最大的代币完成");
                // 如果有多个timeframe，添加小暂停
                if (timeframe.equals(TREND_TIMEFRAMES[0]) && TREND_TIMEFRAMES.length > 1) {
                    sleep(apiCallInterval);
                }
            } catch (Exception e) {
                log.error("同步交易量最大的代币发生错误", e);
            }
        }
        
    }
    
    /**
     * 同步最新代币数据 (内部方法)
     */
    @Transactional
    public void syncLatestTokensInternal() {
        log.info("同步最新代币");
        try {
            List<TokenInfoDto> latestTokens = solanaTrackerService.getLatestTokens();
            
            if (latestTokens.isEmpty()) {
                log.warn("获取到的最新代币为空列表，可能是API限流或其他错误");
                return;
            }
                        
            for (int i = 0; i < latestTokens.size(); i++) {
                TokenInfoDto tokenInfo = latestTokens.get(i);
                String mintAddress = tokenInfo.getToken().getMint();
                
                try {
                    // 保存代币基本信息和相关数据
                    saveTokenData(mintAddress, tokenInfo);
                } catch (Exception e) {
                    log.error("保存代币数据失败: {}", mintAddress, e);
                }
            }
            
            log.info("同步最新代币完成");
        } catch (Exception e) {
            log.error("同步最新代币发生错误", e);
        }
    }
    
    /**
     * 睡眠辅助方法，处理中断异常
     */
    private void sleep(long milliseconds) {
        try {
            TimeUnit.MILLISECONDS.sleep(milliseconds);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("睡眠被中断", e);
        }
    }

    // 原有的@Scheduled方法保留但注释掉，可以在需要时恢复使用
    /*
    @Scheduled(fixedRateString = "${app.scheduler.trending-tokens.interval:120000}")
    @Transactional
    public void syncTrendingTokens() {
        syncTrendingTokensInternal();
    }

    @Scheduled(fixedRateString = "${app.scheduler.volume-tokens.interval:120000}")
    @Transactional
    public void syncVolumeTokens() {
        syncVolumeTokensInternal();
    }
    
    @Scheduled(fixedRateString = "${app.scheduler.latest-tokens.interval:180000}")
    @Transactional
    public void syncLatestTokens() {
        syncLatestTokensInternal();
    }
    */
    
    /**
     * 保存单个代币的所有数据
     */
    @Transactional
    public void saveTokenData(String mintAddress, TokenInfoDto tokenInfo) {
        // 保存基本信息
        saveToken(mintAddress, tokenInfo);
        
        // 保存市场数据 (只取第一个池子)
        if (tokenInfo.getPools() != null && !tokenInfo.getPools().isEmpty()) {
            saveTokenMarketData(mintAddress, tokenInfo.getPools().get(0));
            
            // 保存交易数据
            TokenInfoDto.PoolInfoDto pool = tokenInfo.getPools().get(0);
            if (pool.getTxns() != null) {
                saveTokenTransactionStats(mintAddress, pool.getTxns());
            }
        }
        
        // 保存价格变化数据
        if (tokenInfo.getEvents() != null) {
            saveTokenPriceChanges(mintAddress, tokenInfo.getEvents());
        }
    }

    /**
     * 使用单独事务更新热门代币榜单
     */
    private void updateTrendingRanking(String timeframe, List<TokenInfoDto> trendingTokens) {
        // 使用TransactionTemplate创建新的事务执行榜单更新
        transactionTemplate.execute(status -> {
            try {
                log.info("开始更新{}热点趋势榜单, 共{}项", timeframe, trendingTokens.size());
                
                // 删除旧榜单
                tokenTrendingRankingRepository.deleteByTimeframe(timeframe);
                
                // 生成批次时间戳
                long batchTimestamp = System.currentTimeMillis();
                
                // 批量保存新榜单
                List<TokenTrendingRanking> rankings = new ArrayList<>();
                for (int i = 0; i < trendingTokens.size(); i++) {
                    TokenTrendingRanking ranking = new TokenTrendingRanking();
                    ranking.setTimeframe(timeframe);
                    ranking.setMintAddress(trendingTokens.get(i).getToken().getMint());
                    ranking.setRanking(i + 1);  // 排名从1开始
                    ranking.setBatchTimestamp(batchTimestamp);
                    rankings.add(ranking);
                }
                
                tokenTrendingRankingRepository.saveAll(rankings);
                log.info("更新{}热点趋势榜单完成", timeframe);
                return true;
            } catch (Exception e) {
                log.error("更新热点趋势榜单事务失败: {}", e.getMessage());
                status.setRollbackOnly();
                return false;
            }
        });
    }
    
    /**
     * 检查文本是否超过指定长度
     * @return 如果超过长度则返回null，否则返回原文本
     */
    private String validateTextLength(String text) {
        if (text != null && text.length() > MAX_NORMAL_STRING_LENGTH) {
            return null;
        }
        return text;
    }
    
    /**
     * 保存代币基本信息
     */
    private void saveToken(String mintAddress, TokenInfoDto tokenInfo) {
        if (tokenInfo == null || tokenInfo.getToken() == null) return;
        
        TokenInfoDto.TokenMetaDto tokenMeta = tokenInfo.getToken();
        
        // 查找已有代币或创建新代币
        Token token = tokenRepository.findByMintAddress(mintAddress)
                .orElse(new Token());
        
        // 更新代币信息
        token.setMintAddress(mintAddress);
        if (tokenMeta.getName() != null) token.setName(validateTextLength(tokenMeta.getName()));
        if (tokenMeta.getSymbol() != null) token.setSymbol(validateTextLength(tokenMeta.getSymbol()));
        if (tokenMeta.getDecimals() != null) token.setDecimals(tokenMeta.getDecimals());
        if (tokenMeta.getImage() != null) token.setImageUrl(validateTextLength(tokenMeta.getImage()));
        // description已经是TEXT类型，可以接受长文本
        if (tokenMeta.getDescription() != null) token.setDescription(tokenMeta.getDescription());
        // 对于普通VARCHAR字段，需要验证长度
        if (tokenMeta.getWebsite() != null) token.setWebsite(validateTextLength(tokenMeta.getWebsite()));
        if (tokenMeta.getTwitter() != null) token.setTwitter(validateTextLength(tokenMeta.getTwitter()));
        if (tokenMeta.getTelegram() != null) token.setTelegram(validateTextLength(tokenMeta.getTelegram()));
        if (tokenMeta.getCreatedOn() != null) token.setCreatedOn(validateTextLength(tokenMeta.getCreatedOn()));
        
        // 获取发布者和创建时间信息（从第一个池子）
        if (tokenInfo.getPools() != null && !tokenInfo.getPools().isEmpty()) {
            TokenInfoDto.PoolInfoDto pool = tokenInfo.getPools().get(0);
            if (pool.getDeployer() != null) token.setDeployer(validateTextLength(pool.getDeployer()));
            if (pool.getCreatedAt() != null) token.setCreatedAt(pool.getCreatedAt());
        }
        
        // 处理风险信息
        if (tokenInfo.getRisk() != null) {
            TokenInfoDto.RiskInfoDto risk = tokenInfo.getRisk();
            if (risk.getRugged() != null) token.setIsRugged(risk.getRugged());
            if (risk.getScore() != null) token.setRiskScore(risk.getScore());
            if (risk.getRisks() != null) token.setRiskItems(convertRiskItemsToJson(risk.getRisks()));
        }
        
        token.setTimestamp(System.currentTimeMillis());
        
        try {
            tokenRepository.save(token);
        } catch (Exception e) {
            log.error("保存代币基本信息失败: {} ({})", mintAddress, e.getMessage());
        }
    }
    
    /**
     * 保存代币市场数据
     */
    private void saveTokenMarketData(String mintAddress, TokenInfoDto.PoolInfoDto poolInfo) {
        if (poolInfo == null) return;
        
        TokenMarketData marketData = tokenMarketDataRepository.findByMintAddress(mintAddress)
                .orElse(new TokenMarketData());
        
        marketData.setMintAddress(mintAddress);
        marketData.setPoolId(validateTextLength(poolInfo.getPoolId()));
        marketData.setMarket(validateTextLength(poolInfo.getMarket()));
        
        if (poolInfo.getPrice() != null) {
            marketData.setPriceQuote(poolInfo.getPrice().getQuote());
            marketData.setPriceUsd(poolInfo.getPrice().getUsd());
        }
        
        if (poolInfo.getLiquidity() != null) {
            marketData.setLiquidityQuote(poolInfo.getLiquidity().getQuote());
            marketData.setLiquidityUsd(poolInfo.getLiquidity().getUsd());
        }
        
        if (poolInfo.getMarketCap() != null) {
            marketData.setMarketCapQuote(poolInfo.getMarketCap().getQuote());
            marketData.setMarketCapUsd(poolInfo.getMarketCap().getUsd());
        }
        
        marketData.setTokenSupply(poolInfo.getTokenSupply());
        marketData.setLpBurn(poolInfo.getLpBurn());
        marketData.setQuoteToken(validateTextLength(poolInfo.getQuoteToken()));
        marketData.setTimestamp(System.currentTimeMillis());
        
        try {
            tokenMarketDataRepository.save(marketData);
        } catch (Exception e) {
            log.error("保存代币市场数据失败: {} ({})", mintAddress, e.getMessage());
        }
    }
    
    /**
     * 保存代币价格变化数据
     */
    private void saveTokenPriceChanges(String mintAddress, Map<String, TokenInfoDto.PriceChangeDto> events) {
        if (events == null || events.isEmpty()) return;
        
        for (Map.Entry<String, TokenInfoDto.PriceChangeDto> entry : events.entrySet()) {
            String timeframe = entry.getKey();
            TokenInfoDto.PriceChangeDto priceChange = entry.getValue();
            
            if (priceChange == null || priceChange.getPriceChangePercentage() == null) continue;
            
            TokenPriceChange tokenPriceChange = tokenPriceChangeRepository
                    .findByMintAddressAndTimeframe(mintAddress, timeframe)
                    .orElse(new TokenPriceChange());
            
            tokenPriceChange.setMintAddress(mintAddress);
            tokenPriceChange.setTimeframe(validateTextLength(timeframe));
            tokenPriceChange.setPriceChangePercentage(priceChange.getPriceChangePercentage());
            tokenPriceChange.setTimestamp(System.currentTimeMillis());
            
            try {
                tokenPriceChangeRepository.save(tokenPriceChange);
            } catch (Exception e) {
                log.error("保存代币价格变化数据失败: {} {} ({})", mintAddress, timeframe, e.getMessage());
            }
        }
    }
    
    /**
     * 保存代币交易统计数据
     */
    private void saveTokenTransactionStats(String mintAddress, TokenInfoDto.TransactionDto txns) {
        if (txns == null) return;
        
        TokenTransactionStats stats = tokenTransactionStatsRepository.findByMintAddress(mintAddress)
                .orElse(new TokenTransactionStats());
        
        stats.setMintAddress(mintAddress);
        stats.setBuys(txns.getBuys());
        stats.setSells(txns.getSells());
        stats.setTotalTxns(txns.getTotal());
        stats.setVolume(txns.getVolume());
        stats.setTimestamp(System.currentTimeMillis());
        
        try {
            tokenTransactionStatsRepository.save(stats);
        } catch (Exception e) {
            log.error("保存代币交易统计数据失败: {} ({})", mintAddress, e.getMessage());
        }
    }
    
    /**
     * 将风险项列表转换为JSON字符串
     */
    private String convertRiskItemsToJson(List<TokenInfoDto.RiskItemDto> riskItems) {
        if (riskItems == null || riskItems.isEmpty()) {
            return "[]";
        }
        
        // 这里简化处理，实际应使用JSON库如Jackson
        StringBuilder json = new StringBuilder("[");
        for (int i = 0; i < riskItems.size(); i++) {
            TokenInfoDto.RiskItemDto item = riskItems.get(i);
            json.append("{\"name\":\"").append(item.getName()).append("\",");
            json.append("\"description\":\"").append(item.getDescription()).append("\",");
            json.append("\"level\":\"").append(item.getLevel()).append("\",");
            json.append("\"score\":").append(item.getScore()).append("}");
            
            if (i < riskItems.size() - 1) {
                json.append(",");
            }
        }
        json.append("]");
        
        return json.toString();
    }
} 