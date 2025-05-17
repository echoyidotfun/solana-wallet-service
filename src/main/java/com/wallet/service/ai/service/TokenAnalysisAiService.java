package com.wallet.service.ai.service;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

// Import DTO if we plan to parse structured output directly, for now, we handle text.
// import com.wallet.service.ai.dto.AggregatedTokenDataDto;

@Service
public class TokenAnalysisAiService {

    private final ChatClient chatClient;

    /**
     * Constructs the AI service with a ChatClient.
     * The ChatClient is configured via Spring AI auto-configuration
     * and properties in application.yml (like API key, model).
     * The function 'getTokenAnalysisData' (defined as a FunctionCallback bean
     * named 'tokenAnalysisToolFunction' and exposed to LLM as 'getTokenAnalysisData')
     * will be made available to the LLM in the prompt options.
     *
     * @param chatClientBuilder The builder for creating ChatClient instances, injected by Spring.
     */
    @Autowired
    public TokenAnalysisAiService(ChatClient.Builder chatClientBuilder) {
        // The ChatClient.Builder can be used to set default options,
        // including default functions if they are always available.
        // For this example, we enable the function per call.
        this.chatClient = chatClientBuilder.build();
    }

    /**
     * Analyzes a token based on the provided token symbol or identifier.
     * It instructs the LLM to act as a crypto market analyst and use the
     * 'getTokenAnalysisData' function to fetch detailed information.
     *
     * @param tokenIdentifier The symbol (e.g., "SOL") or contract address of the token.
     * @return A string containing the LLM's analysis.
     */
    public String analyzeToken(String tokenIdentifier) {
        String systemMessageContent = """
                You are an expert crypto market analyst. Your primary goal is to provide a comprehensive narrative summary,
                potential analysis, and market sentiment analysis for a given cryptocurrency token.
                When a user requests an analysis for a token, you MUST use the 'getTokenAnalysisData' function
                to retrieve its detailed on-chain and market data.
                Based on the data returned by the function, provide your analysis.
                If the function returns no data or indicates the token is not found, inform the user clearly.
                Do not try to guess or provide information for a token if the function call fails or returns no data.
                Your final response should be a well-structured analysis.
                """;

        // User's query is implicitly about the tokenIdentifier
        String userQueryContent = "Please provide a detailed analysis for the token: " + tokenIdentifier;

        System.out.println("Sending request to AI for token: " + tokenIdentifier);
        System.out.println("System Message: " + systemMessageContent);
        System.out.println("User Query: " + userQueryContent);

        ChatResponse response = chatClient.prompt()
                .system(systemMessageContent)
                .user(userQueryContent)
                .functions("getTokenAnalysisData") // Make the function available by the name it's exposed to the LLM
                .call()
                .chatResponse();
        
        System.out.println("AI Response received.");

        // In Spring AI with FunctionCallback, the framework typically handles the function execution
        // and re-sends the result to the LLM automatically. The final 'response' here should be
        // the LLM's textual answer after it has (potentially) used the function.

        // We need to inspect the response to see if a tool call was made and what the final content is.
        // For now, we just return the content.
        // Advanced: Check if response.getResult().getOutput().getToolCalls() has anything if auto-execution isn't transparent.
        // However, with FunctionCallbacks, the ChatClient's response should be the final textual answer from the LLM.
        String llmResponseContent = response.getResult().getOutput().getContent();
        System.out.println("LLM Output Content: " + llmResponseContent);
        
        return llmResponseContent;
    }

    /**
     * A more generic method to get chat completion for a given prompt,
     * also enabling the token analysis function.
     *
     * @param userPrompt The user's full query.
     * @return A string containing the LLM's response.
     */
    public String getChatCompletion(String userPrompt) {
        String systemMessageContent = """
            You are an experienced crypto trader. Provide analysis based on available tools.
            If you need data for a token, use the 'getTokenAnalysisData' function.
            """;

        ChatResponse response = chatClient.prompt()
            .system(systemMessageContent)
            .user(userPrompt)
            .functions("getTokenAnalysisData")
            .call()
            .chatResponse();
        
        return response.getResult().getOutput().getContent();
    }
} 