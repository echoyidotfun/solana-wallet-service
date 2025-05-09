package com.wallet.service.datapipe.repository;

import com.wallet.service.datapipe.model.TokenPriceChange;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TokenPriceChangeRepository extends JpaRepository<TokenPriceChange, Long> {
    
    // 根据代币地址和时间范围获取价格变化
    Optional<TokenPriceChange> findByMintAddressAndTimeframe(String mintAddress, String timeframe);
    
    // 根据时间范围查询，按价格变化百分比倒序排序
    List<TokenPriceChange> findByTimeframeOrderByPriceChangePercentageDesc(String timeframe, Pageable pageable);
    
    // 获取指定代币所有时间范围的价格变化数据
    List<TokenPriceChange> findByMintAddress(String mintAddress);
    
    // 获取最新的热门代币列表（根据24小时价格变动），限制条数
    @Query("SELECT tpc FROM TokenPriceChange tpc WHERE tpc.timeframe = '24h' ORDER BY tpc.priceChangePercentage DESC")
    List<TokenPriceChange> findTrendingTokensLast24Hours(Pageable pageable);
    
    // 获取最新的热门代币列表（根据1小时价格变动），限制条数
    @Query("SELECT tpc FROM TokenPriceChange tpc WHERE tpc.timeframe = '1h' ORDER BY tpc.priceChangePercentage DESC")
    List<TokenPriceChange> findTrendingTokensLastHour(Pageable pageable);
}
