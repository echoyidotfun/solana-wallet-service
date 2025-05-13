package com.wallet.service.datapipe.dto;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class TraderDto {
    private List<WalletDataDto> wallets;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class WalletDataDto {
        private String wallet;
        private WalletSummaryDto summary;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class WalletSummaryDto {
        private BigDecimal realized;
        private BigDecimal unrealized;
        private BigDecimal total;
        private BigDecimal totalInvested;
        private Integer totalWins;
        private Integer totalLosses;
        private BigDecimal averageBuyAmount;
        private Double winPercentage;
        private Double lossPercentage;
        private Double neutralPercentage;

        public BigDecimal getRoi() {
            return this.total.divide(this.totalInvested, 4, RoundingMode.HALF_UP);
        }
    }
}


