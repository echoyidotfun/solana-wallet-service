package com.wallet.service.datapipe.dto.quicknode;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;

/**
 * QuickNode交易事件DTO（新版，严格对齐QuickNode webhook结构）
 */
@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class QuickNodeTransactionEventDto {
    private Long blockTime;
    private List<MatchedTransaction> matchedTransactions;

    @Data
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class MatchedTransaction {
        private Long blockTime;
        private Long slot;
        private List<MatchedInstruction> matchedInstructions;
        private List<Long> preBalances;
        private List<Long> postBalances;
        private List<TokenBalance> preTokenBalances;
        private List<TokenBalance> postTokenBalances;
        private String signature;
        private String trackedAddress;
    }

    @Data
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class MatchedInstruction {
        private List<String> accounts;
        private String decodedDataHex;
        private String instructionType;
        private String programId;
        private String rawData;
    }

    @Data
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class TokenBalance {
        private Integer accountIndex;
        private String mint;
        private String owner;
        private String programId;
        private UiTokenAmount uiTokenAmount;
    }

    @Data
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class UiTokenAmount {
        private String amount;
        private Integer decimals;
        private Double uiAmount;
        private String uiAmountString;
    }
} 