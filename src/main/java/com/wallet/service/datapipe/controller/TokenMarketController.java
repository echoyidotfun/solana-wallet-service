package com.wallet.service.datapipe.controller;

import com.wallet.service.common.dto.ApiResponse;
import com.wallet.service.datapipe.service.TokenDataService;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Map;

/**
 * 代币市场数据API控制器
 */
@RestController
@RequestMapping("/api/v1/market")
@RequiredArgsConstructor
@Slf4j
public class TokenMarketController {
    
    private final TokenDataService tokenDataService;
    
    /**
     * 获取热点代币榜单
     *
     * @param timeframe 时间范围，默认1h，可选24h
     * @param limit 返回数量，默认50
     * @return 热点代币列表
     */
    @GetMapping("/trending")
    public ResponseEntity<?> getTrendingTokens(
            @RequestParam(defaultValue = "1h") String timeframe,
            @RequestParam(defaultValue = "50") int limit) {
        
        log.info("获取热点代币榜单，timeframe={}, limit={}", timeframe, limit);
        List<Map<String, Object>> trendingTokens = tokenDataService.getTrendingTokens(timeframe, limit);
        return ResponseEntity.ok(new ApiResponse<>(true, "成功获取热点代币榜单", trendingTokens));
    }
    
    /**
     * 获取交易量榜单
     *
     * @param timeframe 时间范围，默认24h，可选1h
     * @param limit 返回数量，默认50
     * @return 交易量排行榜列表
     */
    @GetMapping("/volume")
    public ResponseEntity<?> getVolumeTokens(
            @RequestParam(defaultValue = "24h") String timeframe,
            @RequestParam(defaultValue = "50") int limit) {
        
        log.info("获取交易量榜单，timeframe={}, limit={}", timeframe, limit);
        List<Map<String, Object>> volumeTokens = tokenDataService.getVolumeTokens(timeframe, limit);
        return ResponseEntity.ok(new ApiResponse<>(true, "成功获取交易量榜单", volumeTokens));
    }
    
    /**
     * 获取最新代币列表
     *
     * @param limit 返回数量，默认50
     * @return 最新代币列表
     */
    @GetMapping("/latest")
    public ResponseEntity<?> getLatestTokens(
            @RequestParam(defaultValue = "50") int limit) {
        
        log.info("获取最新代币列表，limit={}", limit);
        List<Map<String, Object>> latestTokens = tokenDataService.getLatestTokens(limit);
        return ResponseEntity.ok(new ApiResponse<>(true, "成功获取最新代币列表", latestTokens));
    }
    
    /**
     * 获取代币详细信息
     *
     * @param mintAddress 代币地址
     * @return 代币详细信息
     */
    @GetMapping("/token/{mintAddress}")
    public ResponseEntity<?> getTokenInfo(
            @PathVariable String mintAddress) {
        
        log.info("获取代币详细信息，mintAddress={}", mintAddress);
        Map<String, Object> tokenInfo = tokenDataService.getTokenInfo(mintAddress);
        
        if (tokenInfo == null) {
            return ResponseEntity.ok(new ApiResponse<>(false, "未找到指定代币信息", null));
        }
        
        return ResponseEntity.ok(new ApiResponse<>(true, "成功获取代币信息", tokenInfo));
    }
}