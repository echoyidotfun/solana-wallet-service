package com.wallet.service.datapipe.dto.quicknode.wss;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;

// This DTO represents the first parameter of logsSubscribe, which defines the filter criteria.
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LogsFilterDto {
    // For subscribing to logs that mention specific account(s)
    private List<String> mentions;
    // To subscribe to all logs (alternative to mentions)
    // private String all; // Can be "all" or "allWithVotes", or null if using mentions

    // Convenience constructor for mentions removed to avoid conflict with Lombok's @AllArgsConstructor
    // public LogsFilterDto(List<String> mentions) {
    //     this.mentions = mentions;
    // }
} 