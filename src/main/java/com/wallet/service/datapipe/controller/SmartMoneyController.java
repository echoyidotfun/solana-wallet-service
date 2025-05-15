package com.wallet.service.datapipe.controller;

import java.util.List;
import java.util.Optional;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.wallet.service.datapipe.dto.txs.TokenRankingDto;
import com.wallet.service.datapipe.dto.txs.WalletTransactionDto;
import com.wallet.service.datapipe.service.SmartMoneyService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 提供查询聪明钱钱包交易数据的接口
 */
@RestController
@RequestMapping("/api/v1/smart-money")
@Slf4j
@RequiredArgsConstructor
public class SmartMoneyController {
    private final SmartMoneyService smartMoneyService;
    
    /**
     * 1. 查询某个聪明钱地址的最近交易
     * @param walletAddress 聪明钱钱包地址
     * @param limit 返回条数 (可选, 默认20)
     */
    @GetMapping("/wallets/{walletAddress}")
    public ResponseEntity<List<WalletTransactionDto>> getWalletTransactions(
            @PathVariable String walletAddress,
            @RequestParam(required = false) Optional<Integer> limit) {
        log.info("API Request: Get transactions for wallet {} with limit {}", walletAddress, limit);
        List<WalletTransactionDto> transactions = smartMoneyService.getWalletTransactions(walletAddress, limit);
        return ResponseEntity.ok(transactions);
    }

    /**
     * 2. 查询近N小时内交易最频繁的代币排行
     * @param hours 小时数 (可选, 默认1)
     * @param limit 返回条数 (可选, 默认10)
     */
    @GetMapping("/tokens/trending")
    public ResponseEntity<List<TokenRankingDto>> getTrendingTokens(
            @RequestParam(required = false) Optional<Integer> hours,
            @RequestParam(required = false) Optional<Integer> limit) {
        log.info("API Request: Get trending tokens ({} hours, limit {})", hours, limit);
        List<TokenRankingDto> rankings = smartMoneyService.getTrendingTokens(hours, limit);
        return ResponseEntity.ok(rankings);
    }

    /**
     * 3. 查询近N小时内买入最多的代币排行
     * @param hours 小时数 (可选, 默认24)
     * @param limit 返回条数 (可选, 默认10)
     */
    @GetMapping("/tokens/top-inflow")
    public ResponseEntity<List<TokenRankingDto>> getTopInflowTokens(
            @RequestParam(required = false) Optional<Integer> hours,
            @RequestParam(required = false) Optional<Integer> limit) {
        log.info("API Request: Get top bought tokens ({} hours, limit {})", hours, limit);
        List<TokenRankingDto> rankings = smartMoneyService.getTopInflowTokens(hours, limit);
        return ResponseEntity.ok(rankings);
    }

    /**
     * 4. 查询近N小时内卖出最多的代币排行
     * @param hours 小时数 (可选, 默认1)
     * @param limit 返回条数 (可选, 默认10)
     */
    @GetMapping("/tokens/top-outflow")
    public ResponseEntity<List<TokenRankingDto>> getTopOutflowTokens(
            @RequestParam(required = false) Optional<Integer> hours,
            @RequestParam(required = false) Optional<Integer> limit) {
        log.info("API Request: Get top sold tokens ({} hours, limit {})", hours, limit);
        List<TokenRankingDto> rankings = smartMoneyService.getTopOutflowTokens(hours, limit);
        return ResponseEntity.ok(rankings);
    }
        
    

} 