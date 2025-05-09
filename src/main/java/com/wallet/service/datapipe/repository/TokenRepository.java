package com.wallet.service.datapipe.repository;

import com.wallet.service.datapipe.model.Token;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TokenRepository extends JpaRepository<Token, Long> {
    
    Optional<Token> findByMintAddress(String mintAddress);
    
    boolean existsByMintAddress(String mintAddress);
    
    // 获取最新创建的代币列表
    @Query("SELECT t FROM Token t ORDER BY t.createdAt DESC")
    List<Token> findLatestTokens(Pageable pageable);

    // 根据创建时间降序查询最新代币
    List<Token> findAllByOrderByCreatedAtDesc(Pageable pageable);
    
    // 根据代币符号模糊查询
    List<Token> findBySymbolContainingIgnoreCase(String symbol, Pageable pageable);
    
    // 根据代币名称模糊查询
    List<Token> findByNameContainingIgnoreCase(String name, Pageable pageable);
}