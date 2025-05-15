package com.wallet.service.datapipe.client.quicknode.http;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wallet.service.datapipe.dto.rpc.SolanaTransactionResponseDto;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@Slf4j
public class SolanaRpcServiceImpl implements SolanaRpcService {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper(); // For constructing JSON RPC request

    @Value("${quicknode.http}")
    private String quicknodeHttpUrl;

    @Value("${quicknode.api-key}")
    private String quicknodeApiKey;

    public SolanaRpcServiceImpl(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    @Override
    public Optional<SolanaTransactionResponseDto> getTransactionDetails(String signature) {
        String fullUrl = quicknodeHttpUrl + quicknodeApiKey; // Combine base URL and API key

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        // Construct JSON-RPC request body for getTransaction
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("jsonrpc", "2.0");
        requestBody.put("id", 1); // Static ID, or use a counter
        requestBody.put("method", "getTransaction");
        
        Map<String, String> config = new HashMap<>();
        config.put("encoding", "jsonParsed");
        config.put("commitment", "confirmed"); // Or "finalized"
        // To get token balances, maxSupportedTransactionVersion is crucial
        // The API docs say it defaults to legacy if not provided. To ensure we get parsed data including token balances:
        // config.put("maxSupportedTransactionVersion", "0"); // Use 0 for latest versioned transactions

        List<Object> params = List.of(
            signature,
            Map.of(
                "encoding", "jsonParsed",
                "commitment", "confirmed", // Or "finalized"
                "maxSupportedTransactionVersion", 0 // Crucial for parsed token balances
            )
        );
        requestBody.put("params", params);

        try {
            String jsonRequest = objectMapper.writeValueAsString(requestBody);
            HttpEntity<String> entity = new HttpEntity<>(jsonRequest, headers);

            log.debug("向 {} 发送 getTransaction 请求: {}", getRedactedUrl(fullUrl), jsonRequest);
            ResponseEntity<SolanaTransactionResponseDto> response = restTemplate.postForEntity(fullUrl, entity, SolanaTransactionResponseDto.class);
            
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                if (response.getBody().getResult() != null) {
                    // log.trace("收到 {} 的 getTransaction 响应: {}", signature, objectMapper.writeValueAsString(response.getBody())); // 日志内容可能非常大，谨慎开启
                    return Optional.of(response.getBody());
                } else {
                    // Handle cases where the RPC call was successful but returned an error object in the JSON response
                    // For example: { "jsonrpc":"2.0", "error":{ "code":-32009, "message":"Transaction version (0) is not supported by the server configuration" }, "id":1 }
                    // We might need a SolanaRpcErrorDto to properly deserialize response.getBody().getError()
                    log.error("获取交易 {} 的RPC响应中包含错误: {}", signature, 
                              response.getBody().toString()); 
                    return Optional.empty();
                }
            } else {
                log.error("获取交易 {} 详情失败。状态码: {}, 响应体: {}", 
                          signature, response.getStatusCode(), response.getBody() != null ? response.getBody().toString() : "null");
                return Optional.empty();
            }

        } catch (Exception e) {
            log.error("调用 getTransaction 获取交易 {} 详情时出错: {}", signature, e.getMessage());
            return Optional.empty();
        }
    }

    private String getRedactedUrl(String originalUrl) {
        if (originalUrl == null || quicknodeApiKey == null || quicknodeApiKey.isEmpty()) {
            return originalUrl;
        }
        // 简单替换，如果API密钥可能出现在URL的其他部分，需要更复杂的逻辑
        return originalUrl.replace(quicknodeApiKey, "<API密钥已隐藏>");
    }
} 