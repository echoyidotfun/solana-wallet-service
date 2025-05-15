package com.wallet.service.datapipe.dto.quicknode.wss;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import java.util.List;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class LogsNotificationDto {
    private String jsonrpc;
    private String method;
    private ParamsDto params;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ParamsDto {
        private ResultDto result;
        private long subscription; // Subscription ID
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ResultDto {
        private ContextDto context;
        private ValueDto value;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ContextDto {
        private long slot;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ValueDto {
        private String signature;
        private List<String> logs;
        private Object err; // Can be null or an error object / json string from the node
    }
} 