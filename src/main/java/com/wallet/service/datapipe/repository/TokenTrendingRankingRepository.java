package com.wallet.service.datapipe.repository;

import com.wallet.service.datapipe.model.TokenTrendingRanking;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TokenTrendingRankingRepository extends JpaRepository<TokenTrendingRanking, Long> {
    
    void deleteByTimeframe(String timeframe);
    
    List<TokenTrendingRanking> findByTimeframeOrderByRankingAsc(String timeframe);
}
