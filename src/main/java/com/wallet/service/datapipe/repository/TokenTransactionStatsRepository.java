package com.wallet.service.datapipe.repository;

import com.wallet.service.datapipe.model.TokenTransactionStats;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TokenTransactionStatsRepository extends JpaRepository<TokenTransactionStats, Long> {
    
    // 根据代币地址查询交易统计数据
    Optional<TokenTransactionStats> findByMintAddress(String mintAddress);
    
    // 按交易量降序查询交易量最大的代币列表
    @Query("SELECT t FROM TokenTransactionStats t ORDER BY t.volume DESC")
    List<TokenTransactionStats> findTopByVolumeDesc(Pageable pageable);
    
    // 按交易数量降序查询交易最活跃的代币列表
    @Query("SELECT t FROM TokenTransactionStats t ORDER BY t.totalTxns DESC")
    List<TokenTransactionStats> findTopByTotalTxnsDesc(Pageable pageable);
    
    // 按购买交易数降序查询代币列表
    @Query("SELECT t FROM TokenTransactionStats t ORDER BY t.buys DESC")
    List<TokenTransactionStats> findTopByBuysDesc(Pageable pageable);
    
    // 按卖出交易数降序查询代币列表
    @Query("SELECT t FROM TokenTransactionStats t ORDER BY t.sells DESC")
    List<TokenTransactionStats> findTopBySellsDesc(Pageable pageable);
}