package com.wallet.service.ai.controller;

import com.wallet.service.ai.service.TokenAnalysisAiService;

import lombok.RequiredArgsConstructor;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
public class AiController {

    private final TokenAnalysisAiService tokenAnalysisAiService;

    /**
     * Endpoint to get an AI-generated analysis for a specific token.
     * Example: GET /api/ai/analyze/token?identifier=SOL
     *
     * @param ticker The token symbol (e.g., "SOL") or contract address.
     * @return A map containing the AI-generated analysis.
     */
    @GetMapping("/analyze/token")
    public ResponseEntity<?> analyzeToken(@RequestParam String ticker) {
        if (ticker == null || ticker.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Token ticker must be provided."));
        }
        try {
            String analysis = tokenAnalysisAiService.analyzeToken(ticker);
            return ResponseEntity.ok(Map.of("token", ticker, "analysis", analysis));
        } catch (Exception e) {
            // Log the exception details for server-side debugging
            System.err.println("Error during AI token analysis for '" + ticker + "': " + e.getMessage());
            e.printStackTrace(); 
            // Return a generic error message to the client
            return ResponseEntity.internalServerError().body(Map.of("error", "An unexpected error occurred while analyzing the token.", "details", e.getMessage()));
        }
    }

    /**
     * Generic endpoint to get a chat completion for a given prompt.
     * Example: GET /api/ai/chat?prompt=What is the current sentiment for SOL?
     *
     * @param prompt The user's query.
     * @return A map containing the AI-generated response.
     */
    @GetMapping("/chat")
    public ResponseEntity<?> chat(@RequestParam(name = "prompt") String prompt) {
        if (prompt == null || prompt.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Prompt must be provided."));
        }
        try {
            String response = tokenAnalysisAiService.getChatCompletion(prompt);
            return ResponseEntity.ok(Map.of("prompt", prompt, "response", response));
        } catch (Exception e) {
            System.err.println("Error during AI chat completion for prompt '" + prompt + "': " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.internalServerError().body(Map.of("error", "An unexpected error occurred during chat completion.", "details", e.getMessage()));
        }
    }
} 