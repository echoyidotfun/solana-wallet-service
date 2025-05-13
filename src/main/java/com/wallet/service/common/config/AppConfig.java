package com.wallet.service.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.boot.web.client.RestTemplateBuilder;

import java.time.Duration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.DeserializationFeature;

/**
 * 应用全局配置类
 */
@Configuration
@EnableRetry
public class AppConfig {
    
    /**
     * 创建ClientHttpRequestFactory，设置超时
     */
    @Bean
    public ClientHttpRequestFactory clientHttpRequestFactory() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        // 连接超时 5 秒
        factory.setConnectTimeout(5000);
        // 读取超时 10 秒
        factory.setReadTimeout(10000);
        return factory;
    }
    
    /**
     * 创建RestTemplate Bean
     * 用于HTTP请求，配置了超时和重试机制
     */
    @Bean
    public RestTemplate restTemplate(ClientHttpRequestFactory clientHttpRequestFactory) {
        return new RestTemplateBuilder()
                .requestFactory(() -> clientHttpRequestFactory)
                .setConnectTimeout(Duration.ofSeconds(5))
                .setReadTimeout(Duration.ofSeconds(10))
                .build();
    }
    
    /**
     * 确保ObjectMapper Bean可用
     * 配置为忽略未知属性
     */
    @Bean
    public ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        return mapper;
    }
} 