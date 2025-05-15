package com.wallet.service.datapipe.service.sync;

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
import com.wallet.service.datapipe.dto.TraderDto;
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
import com.wallet.service.datapipe.model.Trader;
import com.wallet.service.datapipe.repository.TraderRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Set;
import java.util.HashSet;
import java.util.stream.Collectors;

/**
 * 代币数据同步服务，定时从数据源获取并更新数据库
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class DataSyncService {
    
    private final SolanaTrackerService solanaTrackerService;
    private final TokenRepository tokenRepository;
    private final TokenMarketDataRepository tokenMarketDataRepository;
    private final TokenPriceChangeRepository tokenPriceChangeRepository;
    private final TokenTransactionStatsRepository tokenTransactionStatsRepository;
    private final TokenTrendingRankingRepository tokenTrendingRankingRepository;
    private final TransactionTemplate transactionTemplate;
    private final TraderRepository traderRepository;
    
    // 热点趋势代币时间范围
    private static final String[] TREND_TIMEFRAMES = {"1h", "24h"};
    
    // 文本字段最大长度限制
    private static final int MAX_NORMAL_STRING_LENGTH = 255; // 普通VARCHAR字段的最大长度
    
    // API调用间隔(毫秒)，防止限流
    @Value("${app.scheduler.api-call-interval:2000}")
    private long apiCallInterval;
    
    // 定时任务参数 - 聪明钱钱包同步
    private static final int TRADER_PAGES_TO_FETCH = 10;
    private static final BigDecimal MIN_PROFITABILITY_FOR_TRADER = new BigDecimal("0.20"); // 20%
    private static final Double MIN_WIN_PERCENTAGE_FOR_TRADER = 50.0; // 50%
    
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
            
            sleep(apiCallInterval);
            
            // 2. 同步交易量代币
            syncVolumeTokensInternal();
            
            sleep(apiCallInterval);
            
            // 3. 同步最新代币
            syncLatestTokensInternal();

            sleep(apiCallInterval);
            
            // 4. 同步顶级交易者/聪明钱数据
            syncTraderDataInternal();
            
            log.info(">>>>>>统一数据同步任务完成<<<<<<");
        } catch (Exception e) {
            log.error("!!!!!!统一数据同步任务执行失败!!!!!!", e.getMessage());
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
                log.error("同步{}热点趋势代币发生错误", timeframe, e.getMessage());
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
                log.error("同步交易量最大的代币发生错误", e.getMessage());
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
                    log.error("保存代币数据失败: {}", mintAddress, e.getMessage());
                }
            }
            
            log.info("同步最新代币完成");
        } catch (Exception e) {
            log.error("同步最新代币发生错误", e.getMessage());
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
            log.error("保存代币市场数据时发生未预期错误，已跳过该记录。代币地址: {}. 错误详情: ", mintAddress, e);
            // 这条市场数据将不会被保存，程序会继续处理下一个代币/数据。
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

    /**
     * 同步顶级交易者数据 (内部方法)
     * 从SolanaTracker API获取顶级交易者数据，并根据盈利能力和胜率筛选。
     */
    @Transactional
    public void syncTraderDataInternal() {
        log.info("开始同步顶级交易者数据");
        int walletsProcessed = 0;
        int tradersIdentified = 0;

        try {
            for (int page = 1; page <= TRADER_PAGES_TO_FETCH; page++) {
                log.info("获取顶级交易者数据 - 第 {} 页", page);
                TraderDto response = solanaTrackerService.getTopTraders(page, "total", false);

                if (response == null || response.getWallets() == null || response.getWallets().isEmpty()) {
                    log.warn("从API获取的顶级交易者数据为空或无效 (第 {} 页)，停止当前页处理。", page);
                    if (page < TRADER_PAGES_TO_FETCH) sleep(apiCallInterval); 
                    continue;
                }

                for (TraderDto.WalletDataDto walletData : response.getWallets()) {
                    if (walletData == null || walletData.getWallet() == null || walletData.getSummary() == null) {
                        log.warn("发现无效的钱包数据，跳过。Wallet: {}, Summary: {}", walletData != null ? walletData.getWallet() : "null", walletData != null ? walletData.getSummary() : "null");
                        continue;
                    }
                    walletsProcessed++;
                    TraderDto.WalletSummaryDto summary = walletData.getSummary();
                    String walletAddress = walletData.getWallet();

                    // Initial Filtering Conditions (as before)
                    boolean initialFilterPassed = false;
                    BigDecimal roi = BigDecimal.ZERO;
                    if (summary.getTotalInvested() != null && summary.getTotalInvested().compareTo(BigDecimal.ZERO) > 0 && summary.getRealized() != null) {
                        roi = summary.getRoi();
                        if (roi.compareTo(MIN_PROFITABILITY_FOR_TRADER) > 0) {
                            initialFilterPassed = true;
                        }
                    }
                    boolean initialHighWinRate = summary.getWinPercentage() != null && summary.getWinPercentage() > MIN_WIN_PERCENTAGE_FOR_TRADER;

                    if (initialFilterPassed && initialHighWinRate) {
                        tradersIdentified++;
                        Trader trader = traderRepository.findById(walletAddress)
                                .orElse(new Trader(walletAddress));
                        
                        trader.setRealizedPnl(summary.getRealized());
                        trader.setUnrealizedPnl(summary.getUnrealized());
                        trader.setTotalPnl(summary.getTotal());
                        trader.setTotalInvested(summary.getTotalInvested());
                        trader.setTotalWins(summary.getTotalWins());
                        trader.setTotalLosses(summary.getTotalLosses());
                        trader.setWinPercentage(summary.getWinPercentage());
                        trader.setAverageBuyAmount(summary.getAverageBuyAmount());
                        trader.setLastUpdatedAt(System.currentTimeMillis());
                        trader.setTags(String.join(",", getTags(summary)));

                        traderRepository.save(trader);
                    }
                }
                if (page < TRADER_PAGES_TO_FETCH) {
                    sleep(apiCallInterval);
                }
            }
            log.info("顶级交易者数据同步完成。共处理 {} 个钱包，识别并保存/更新 {} 个交易者。", walletsProcessed, tradersIdentified);
        } catch (Exception e) {
            log.error("同步顶级交易者数据任务执行失败!!!!!!", e);
        }
    }

    private Set<String> getTags(TraderDto.WalletSummaryDto summary) {
        // Enhanced Tagging Logic
        Set<String> tags = new HashSet<>();

        // Specific tags based on new criteria
        if (summary.getWinPercentage() != null && summary.getWinPercentage() > 90) {
            tags.add("超高胜率");
        } else if (summary.getWinPercentage()!= null && summary.getWinPercentage() > 60) {
            tags.add("高胜率");
        }
        
        int totalTxns = 0;
        if (summary.getTotalWins() != null) totalTxns += summary.getTotalWins();
        if (summary.getTotalLosses() != null) totalTxns += summary.getTotalLosses();
        if (totalTxns > 5000) {
            tags.add("高频交易员");
        }
        
        if (summary.getRoi().compareTo(new BigDecimal("2.00")) > 0) { // Profitability > 200%
            tags.add("高盈利率");
        }
        return tags;
    }
} 