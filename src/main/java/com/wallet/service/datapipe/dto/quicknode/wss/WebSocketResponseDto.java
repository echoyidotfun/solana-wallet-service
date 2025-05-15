package com.wallet.service.datapipe.dto.quicknode.wss;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class WebSocketResponseDto {
    private String jsonrpc;
    private Long id; // Matched with request id
    private Object result; // Changed from Long to Object to handle boolean for unsubscribe and Long for subscribe
    private ErrorDto error;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ErrorDto {
        private int code;
        private String message;
    }
} 