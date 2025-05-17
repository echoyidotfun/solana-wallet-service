package com.wallet.service.ai.dto;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

/**
 * Request DTO for the getTokenAnalysisData function.
 */
public record TokenAnalysisRequest(
    @JsonPropertyDescription("The symbol (e.g., \"SOL\") or contract address of the token to query.")
    String tokenIdentifier
) {} 