package com.wallet.service.datapipe.service.sync;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wallet.service.datapipe.dto.TokenInfoDto;
import com.wallet.service.datapipe.dto.TraderDto;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import java.math.BigDecimal;
import lombok.Data;

/**
 * SolanaTracker API调用服务
 * 负责调用SolanaTracker API并将返回数据转换为统一的TokenInfoDto格式
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class SolanaTrackerService {
    
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    
    @Value("${solanatracker.api.key}")
    private String apiKey;
    
    @Value("${solanatracker.api.baseUrl:https://data.solanatracker.io}")
    private String BASE_URL;
    
    // Cache for failed token queries
    private final Map<String, Long> failedTokenQueryCache = new ConcurrentHashMap<>();
    @Value("${solanatracker.api.failed-token-block-duration-minutes:30}")
    private long failedTokenBlockDurationMinutes;

    private long getFailedTokenBlockDurationMs() {
        return TimeUnit.MINUTES.toMillis(failedTokenBlockDurationMinutes);
    }
    
    // 限流控制 - 免费方案每秒1个请求
    private final long API_RATE_LIMIT_MS = 1100; // 略大于1秒，确保不超限
    private long lastRequestTime = 0;
    
    /**
     * 获取热点趋势代币
     * @param timeframe 时间范围，1h或24h
     * @return 热点趋势代币列表
     */
    public List<TokenInfoDto> getTrendingTokens(String timeframe) {
        String url = BASE_URL + "/tokens/trending/" + timeframe;
        return fetchTokenList(url);
    }
    
    /**
     * 获取交易量最大的代币
     * @param timeframe 时间范围，24h
     * @return 交易量最大的代币列表
     */
    public List<TokenInfoDto> getVolumeTokens(String timeframe) {
        String url = BASE_URL + "/tokens/volume/" + timeframe;
        return fetchTokenList(url);
    }
    
    /**
     * 获取最新的代币
     * @return 最新代币列表
     */
    public List<TokenInfoDto> getLatestTokens() {
        String url = BASE_URL + "/tokens/latest";
        return fetchTokenList(url);
    }
    
    /**
     * 获取顶级交易者列表
     * @param page 页码 (1-indexed)
     * @param sortBy 排序字段 ("total" or "winpercentage")
     * @param expandPnl 是否展开PNL详情
     * @return 顶级交易者数据列表
     */
    public TraderDto getTopTraders(int page, String sortBy, boolean expandPnl) {
        String url = BASE_URL + "/top-traders/all/" + page + "?sortBy=" + sortBy + "&expandPnl=" + expandPnl;
        try {
            ResponseEntity<String> response = makeApiCall(url);
            if (response.getBody() == null || response.getBody().isEmpty() || response.getBody().equals("{}")) {
                 log.warn("获取顶级交易者数据返回为空或无效: {}, page {}", url, page);
                 return new TraderDto(); // Return empty DTO
            }
            return objectMapper.readValue(response.getBody(), TraderDto.class);
        } catch (HttpClientErrorException.NotFound e) {
            log.warn("顶级交易者数据未找到 (404): {}, page {}", url, page);
            return new TraderDto();
        } catch (ResourceAccessException e) {
            log.debug("获取顶级交易者数据时连接超时: {}, page {}", url, page);
            return new TraderDto();
        } catch (Exception e) {
            log.error("获取顶级交易者数据失败: {}, page {}, Error: {}", url, page, e.getMessage());
            return new TraderDto();
        }
    }
    
    /**
     * 获取指定代币信息
     * @param mintAddress 代币地址
     * @return 代币信息
     */
    public TokenInfoDto getTokenInfo(String mintAddress) {
        // Check cache for temporarily blocked tokens
        Long failureTimestamp = failedTokenQueryCache.get(mintAddress);
        if (failureTimestamp != null) {
            if (System.currentTimeMillis() < failureTimestamp + getFailedTokenBlockDurationMs()) {
                log.warn("代币 {} 由于近期查询失败或网络问题，暂时被屏蔽，跳过本次API查询。", mintAddress);
                return null;
            } else {
                failedTokenQueryCache.remove(mintAddress); // Expired
                log.info("代币 {} 的查询屏蔽已过期，将尝试重新查询。", mintAddress);
            }
        }

        String url = BASE_URL + "/tokens/" + mintAddress;
        
        try {
            ResponseEntity<String> response = makeApiCall(url);
            
            // Check if response from makeApiCall (after retries and recovery) is valid
            if (response == null || response.getBody() == null || response.getBody().isEmpty() || 
                response.getBody().equals("{}") || response.getBody().equals("[]")) {
                 log.warn("从SolanaTracker API获取代币 {} 的响应为空或无效，即使在重试后。将临时屏蔽。", mintAddress);
                 failedTokenQueryCache.put(mintAddress, System.currentTimeMillis());
                 return null;
            }

            TokenInfoDto tokenInfo = objectMapper.readValue(response.getBody(), TokenInfoDto.class);

            // Validate if meaningful data was parsed
            if (tokenInfo != null && tokenInfo.getToken() != null && tokenInfo.getToken().getMint() != null) {
                // If successfully parsed and was in cache (e.g. due to previous temporary error), remove it.
                if (failedTokenQueryCache.containsKey(mintAddress)) {
                    failedTokenQueryCache.remove(mintAddress);
                    log.info("代币 {} 查询成功，已从屏蔽缓存中移除。", mintAddress);
                }
                return tokenInfo;
            } else {
                log.warn("解析后未能从代币 {} 获取有效元数据 (可能响应为空或结构不符)，将临时屏蔽。", mintAddress);
                failedTokenQueryCache.put(mintAddress, System.currentTimeMillis());
                return null;
            }
        } catch (HttpClientErrorException.NotFound e) {
            log.warn("代币 {} 不存在 (404)，将临时屏蔽。", mintAddress);
            failedTokenQueryCache.put(mintAddress, System.currentTimeMillis());
            return null;
        } catch (ResourceAccessException e) { // Typically network issues like timeout
            log.warn("获取代币 {} 信息时发生资源访问错误 (如连接超时)，将临时屏蔽。", mintAddress);
            failedTokenQueryCache.put(mintAddress, System.currentTimeMillis());
            return null;
        } catch (JsonProcessingException e) {
            log.error("解析代币 {} 的JSON响应时发生错误，将临时屏蔽。响应体可能无效。Error: {}", mintAddress, e.getMessage());
            failedTokenQueryCache.put(mintAddress, System.currentTimeMillis());
            return null;
        } catch (Exception e) { // Catch-all for other unexpected errors during fetch/parse
            log.error("获取或解析代币 {} 信息时发生未知错误，将临时屏蔽。Error: {}", mintAddress, e.getMessage(), e);
            failedTokenQueryCache.put(mintAddress, System.currentTimeMillis());
            return null;
        }
    }
    
    /**
     * 获取代币列表
     */
    private List<TokenInfoDto> fetchTokenList(String url) {
        try {
            ResponseEntity<String> response = makeApiCall(url);
            return Arrays.asList(objectMapper.readValue(response.getBody(), TokenInfoDto[].class));
        } catch (HttpClientErrorException.TooManyRequests e) {
            log.warn("API请求频率过高，等待后重试: {}", url);
            return new ArrayList<>();
        } catch (ResourceAccessException e) {
            log.debug("获取代币列表时连接超时: {}", url);
            return new ArrayList<>();
        } catch (Exception e) {
            log.error("获取代币列表失败: {}, 错误类型: {}", url, e.getClass().getSimpleName());
            return new ArrayList<>();
        }
    }
    
    /**
     * 执行API调用，带有重试机制
     * 对于429错误（请求过多）进行特殊处理
     */
    @Retryable(
        value = {HttpClientErrorException.TooManyRequests.class, HttpServerErrorException.class, ResourceAccessException.class},
        maxAttempts = 3,
        backoff = @Backoff(delay = 2000, multiplier = 2)
    )
    private ResponseEntity<String> makeApiCall(String url) throws HttpClientErrorException, HttpServerErrorException, ResourceAccessException {
        // 实现简单限流，确保请求间隔不少于API_RATE_LIMIT_MS
        long currentTime = System.currentTimeMillis();
        long elapsedTime = currentTime - lastRequestTime;
        
        if (elapsedTime < API_RATE_LIMIT_MS) {
            try {
                long sleepTime = API_RATE_LIMIT_MS - elapsedTime;
                log.debug("限流控制，等待 {} 毫秒后发送请求: {}", sleepTime, url);
                TimeUnit.MILLISECONDS.sleep(sleepTime);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        
        HttpHeaders headers = new HttpHeaders();
        headers.set("x-api-key", apiKey);
        
        HttpEntity<String> entity = new HttpEntity<>(headers);
        
        try {
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);
            lastRequestTime = System.currentTimeMillis();
            return response;
        } catch (HttpClientErrorException.TooManyRequests e) {
            log.warn("API请求频率过高 (429), 将进行重试: {}", url);
            lastRequestTime = System.currentTimeMillis();
            throw e;
        } catch (ResourceAccessException e) {
            // 连接超时异常，降低日志级别并省略堆栈信息
            log.debug("API连接超时，将进行重试: {}", url);
            throw e;
        } catch (HttpServerErrorException e) {
            log.warn("服务器错误，将进行重试: {}, 错误: {}", url, e.getStatusCode());
            throw e;
        }
    }
    
    /**
     * 重试失败后的恢复方法
     */
    @Recover
    private ResponseEntity<String> recoverFromApiCall(Exception e, String url) {
        if (e instanceof ResourceAccessException) {
            // 连接超时异常，使用debug级别并省略堆栈
            log.debug("API调用重试耗尽，连接超时: {}", url);
        } else {
            log.warn("API调用重试耗尽: {}, 错误类型: {}, 错误消息: {}", url, e.getClass().getSimpleName(), e.getMessage());
        }
        // 返回一个空的成功响应，避免整个服务因为API失败而崩溃
        // For list responses, an empty JSON array "[]" might be more appropriate
        // For object responses, an empty JSON object "{}" is fine
        // Adjust if specific endpoints expect different empty valid responses
        if (url.contains("/tokens/latest") || url.contains("/tokens/trending") || url.contains("/tokens/volume")) {
            return new ResponseEntity<>("[]", HttpStatus.OK);
        }
        return new ResponseEntity<>("{}", HttpStatus.OK);
    }
}
