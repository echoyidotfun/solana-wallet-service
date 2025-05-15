package com.wallet.service.datapipe.dto.rpc;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

// Main DTO for the getTransaction RPC response
@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class SolanaTransactionResponseDto {
    private String jsonrpc;
    private SolanaRpcResult result;
    private Long id;
    // private SolanaRpcError error; // Add if error handling from this DTO is needed

    @Data
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class SolanaRpcResult {
        private Long slot;
        private ParsedTransactionDto transaction;
        private TransactionMetaDto meta;
        private Long blockTime; // Unix timestamp of when the transaction was processed
    }

    @Data
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ParsedTransactionDto {
        private ParsedMessageDto message;
        private List<String> signatures;
    }

    @Data
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ParsedMessageDto {
        @JsonProperty("accountKeys")
        private List<AccountKeyDto> accountKeys;
        private List<ParsedInstructionDto> instructions;
        // recentBlockhash, addressTableLookups etc. can be added if needed
    }

    @Data
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class AccountKeyDto {
        private String pubkey;
        private boolean signer;
        private boolean writable;
        // source (e.g., "transaction", "lookupTable") can be added
    }

    @Data
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ParsedInstructionDto {
        private String programId;
        // For parsed instructions, there might be a "parsed" field with type-specific info
        // For non-parsed, there are "accounts" (list of pubkeys) and "data" (base58 string)
        private ParsedInstructionDetailsDto parsed; // If encoding=jsonParsed, this will be populated for known programs
        private List<String> accounts; // account pubkeys involved in this instruction
        private String data; // Base58 encoded instruction data
    }

    @Data
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ParsedInstructionDetailsDto {
        private String type; // e.g., "transferChecked", "initializeAccount"
        private InstructionInfo info; // Structure varies based on type
    }

    @Data
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class InstructionInfo {
        // This is highly variable. For a transfer, it might contain amount, source, destination, authority.
        // For simplicity, specific fields are omitted here and can be added as needed or handled via a Map<String, Object>.
        private String source;
        private String destination;
        private String authority;
        private String amount;
        private Long lamports;
        private String newAccount;
        // Add other common fields or use a Map if structure is too diverse
        private Map<String, Object> additionalProperties; 
    }


    @Data
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class TransactionMetaDto {
        private Object err; // null if successful, or an error object/string
        private Long fee;
        private List<Long> preBalances;
        private List<Long> postBalances;
        @JsonProperty("preTokenBalances")
        private List<TokenBalanceDto> preTokenBalances;
        @JsonProperty("postTokenBalances")
        private List<TokenBalanceDto> postTokenBalances;
        private List<String> logMessages;
        // innerInstructions, loadedAddresses etc. can be added if needed
    }

    @Data
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class TokenBalanceDto {
        private int accountIndex;
        private String mint;
        private UiTokenAmountDto uiTokenAmount;
        private String owner;
        // programId (for associated token accounts) can be added
    }

    @Data
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class UiTokenAmountDto {
        private String amount; // String representation of the token amount
        private int decimals;
        private Double uiAmount; // Floating point representation
        private String uiAmountString; // String representation of the floating point amount
    }
} 