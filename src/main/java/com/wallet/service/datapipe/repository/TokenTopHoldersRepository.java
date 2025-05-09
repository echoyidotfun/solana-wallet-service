package com.wallet.service.datapipe.repository;

import com.wallet.service.datapipe.model.TokenTopHolders;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface TokenTopHoldersRepository extends JpaRepository<TokenTopHolders, Long> {
    
    // 获取指定代币的顶级持有者数据
    Optional<TokenTopHolders> findByMintAddress(String mintAddress);
}