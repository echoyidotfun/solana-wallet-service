package com.wallet.service.datapipe.repository;

import com.wallet.service.datapipe.model.SmartMoneyTransaction;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.util.List;

@Repository
public interface SmartMoneyTransactionRepository extends JpaRepository<SmartMoneyTransaction, String> {
    
    /**
     * 查找指定钱包地址的所有交易
     */
    List<SmartMoneyTransaction> findByWalletAddressOrderByTransactionTimeDesc(String walletAddress);
    
    /**
     * 查找指定代币的所有交易
     */
    // List<SmartMoneyTransaction> findByTokenMintOrderByTransactionTimeDesc(String tokenMint);
    
    /**
     * 查找一段时间内特定平台的所有交易
     */
    List<SmartMoneyTransaction> findByPlatformAndTransactionTimeBetweenOrderByTransactionTimeDesc(
            String platform, Timestamp startTime, Timestamp endTime);
    
    // Find recent transactions for a specific wallet with limit
    List<SmartMoneyTransaction> findByWalletAddressOrderByTransactionTimeDesc(String walletAddress, Pageable pageable);

    // Find all transactions within a time range (needed for ranking logic)
    List<SmartMoneyTransaction> findByTransactionTimeBetween(Timestamp startTime, Timestamp endTime);

    // Query to get token mints ranked by total transaction count (input OR output) within a time range
    // Returns List<Object[]> where Object[0] is tokenMint, Object[1] is count
    @Query("""
        SELECT tokenMint, COUNT(tokenMint) as txnCount
        FROM (
            SELECT tokenInputMint as tokenMint FROM SmartMoneyTransaction t WHERE t.transactionTime BETWEEN :startTime AND :endTime AND t.tokenInputMint IS NOT NULL AND t.tokenInputMint != 'So11111111111111111111111111111111111111112'
            UNION ALL
            SELECT tokenOutputMint as tokenMint FROM SmartMoneyTransaction t WHERE t.transactionTime BETWEEN :startTime AND :endTime AND t.tokenOutputMint IS NOT NULL AND t.tokenOutputMint != 'So11111111111111111111111111111111111111112'
        ) AS combined_mints
        WHERE tokenMint != 'So11111111111111111111111111111111111111112'
        GROUP BY tokenMint
        ORDER BY txnCount DESC
    """)
    List<Object[]> findTrendingTokensByCount(@Param("startTime") Timestamp startTime, @Param("endTime") Timestamp endTime, Pageable pageable);

    // Query to get token mints ranked by buy transaction count (token is output) within a time range, excluding SOL
    @Query("""
        SELECT t.tokenOutputMint, COUNT(t.signature) as txnCount
        FROM SmartMoneyTransaction t 
        WHERE t.transactionTime BETWEEN :startTime AND :endTime 
          AND t.tokenOutputMint IS NOT NULL
          AND t.tokenOutputMint != 'So11111111111111111111111111111111111111112' 
          AND t.tokenOutputAmount > 0
        GROUP BY t.tokenOutputMint 
        ORDER BY txnCount DESC 
    """)
    List<Object[]> findTopBoughtTokensByCount(@Param("startTime") Timestamp startTime, @Param("endTime") Timestamp endTime, Pageable pageable);

    // Query to get token mints ranked by sell transaction count (token is input) within a time range, excluding SOL
    @Query("""
        SELECT t.tokenInputMint, COUNT(t.signature) as txnCount
        FROM SmartMoneyTransaction t 
        WHERE t.transactionTime BETWEEN :startTime AND :endTime 
          AND t.tokenInputMint IS NOT NULL
          AND t.tokenInputMint != 'So11111111111111111111111111111111111111112' 
          AND t.tokenInputAmount > 0
        GROUP BY t.tokenInputMint 
        ORDER BY txnCount DESC 
    """)
    List<Object[]> findTopSoldTokensByCount(@Param("startTime") Timestamp startTime, @Param("endTime") Timestamp endTime, Pageable pageable);

    // Find transactions involving a specific mint (either input or output) within a time range
    List<SmartMoneyTransaction> findByTransactionTimeBetweenAndTokenInputMintOrTokenOutputMint(Timestamp startTime, Timestamp endTime, String inputMint, String outputMint);
} 