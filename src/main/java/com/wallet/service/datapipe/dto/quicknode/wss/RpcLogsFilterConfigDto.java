package com.wallet.service.datapipe.dto.quicknode.wss;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

// This DTO represents the second parameter of logsSubscribe (optional configuration object)
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL) // Important to omit null fields like commitment if not set
public class RpcLogsFilterConfigDto {
    private String commitment; // e.g., "processed", "confirmed", "finalized"
    private String encoding; // e.g., "base58", "base64", "jsonParsed", "base64+zstd"

    // Constructor for setting commitment - removed to avoid conflict with Lombok
    // public RpcLogsFilterConfigDto(String commitment) {
    //     this.commitment = commitment;
    // }
} 