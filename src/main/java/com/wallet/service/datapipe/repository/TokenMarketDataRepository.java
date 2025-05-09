package com.wallet.service.datapipe.repository;

import com.wallet.service.datapipe.model.TokenMarketData;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface TokenMarketDataRepository extends JpaRepository<TokenMarketData, Long> {
    
    // 获取指定代币的最新市场数据
    Optional<TokenMarketData> findByMintAddress(String mintAddress);
}