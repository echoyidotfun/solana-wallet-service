package com.wallet.service.datapipe.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wallet.service.datapipe.dto.TokenInfoDto;

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
     * 获取指定代币信息
     * @param mintAddress 代币地址
     * @return 代币信息
     */
    public TokenInfoDto getTokenInfo(String mintAddress) {
        String url = BASE_URL + "/tokens/" + mintAddress;
        
        try {
            ResponseEntity<String> response = makeApiCall(url);
            return objectMapper.readValue(response.getBody(), TokenInfoDto.class);
        } catch (HttpClientErrorException.NotFound e) {
            log.warn("代币不存在: {}", mintAddress);
            return null;
        } catch (ResourceAccessException e) {
            // 连接超时异常，不打印详细堆栈信息
            log.debug("获取代币信息时连接超时: {}", mintAddress);
            return null;
        } catch (Exception e) {
            log.error("获取代币信息失败: {}", mintAddress);
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
            log.warn("API调用重试耗尽: {}, 错误类型: {}", url, e.getClass().getSimpleName());
        }
        // 返回一个空的成功响应，避免整个服务因为API失败而崩溃
        return new ResponseEntity<>("{}", HttpStatus.OK);
    }
}
