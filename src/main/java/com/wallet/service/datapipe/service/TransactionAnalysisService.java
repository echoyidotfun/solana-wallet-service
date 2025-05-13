package com.wallet.service.datapipe.service;

import com.wallet.service.datapipe.dto.ParsedTransactionDto;
import com.wallet.service.datapipe.dto.quicknode.QuickNodeTransactionEventDto;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 交易分析服务
 * 负责解析Solana交易数据，提取关键信息如平台、代币、操作类型等
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class TransactionAnalysisService {

    // 已知DEX平台的程序ID
//     JUPITER: "JUP6LkbZbjS1jKKwapdHNy74zcZ3tLUZoi5QNyVTaV4",
//   OKX_DEX: "6m2CDdhRgxpH4WjvdzxAYbGxwdGUz5MziiL5jek2kBma",
//   PUMP: "6EF8rrecthR5Dkzon8Nwu78hRvfCKubJ14M5uBEwF6P",
//   PUMP_AMM: "pAMMBay6oceH9fJKBRHGP5D4bD4sWpmSwMn52FMfXEA",
//   BOOP: "boop8hVGQGqehUK2iVEMEnMrL5RbjywRzHKBmBE7ry4",
//   RAYDIUM: "675kPX9MHTjS2zt1qfr1NYHuzeLXfQM9H24wFSUt1Mp8",
//   RAYDIUM_CAMM: "CAMMCzo5YL8w4VFF8KVHrK22GGUsp5VTaW7grrKgrWqK",
//   RAYDIUM_CPMM: "CPMMoo8L3F4NbTegBCKVNunggL7H1ZpdTHKxQB5qKP1C",
//   RAYDIUM_LAUNCHPAD: "LanMV9sAd7wArD4vJFi2qDdfnVhFxYSUg6eADduJ3uj",
//   METEORA_POOLS: "Eo7WjKq67rjJQSZxS6z3YkapzY3eMj6Xy8X5EQVn5UaB",
    private static final String JUPITER_PROGRAM_ID = "JUP6LkbZbjS1jKKwapdHNy74zcZ3tLUZoi5QNyVTaV4";
    private static final String OKX_DEX_PROGRAM_ID = "6m2CDdhRgxpH4WjvdzxAYbGxwdGUz5MziiL5jek2kBma";
    private static final String PUMP_PROGRAM_ID = "6EF8rrecthR5Dkzon8Nwu78hRvfCKubJ14M5uBEwF6P";
    private static final String PUMP_AMM_PROGRAM_ID = "pAMMBay6oceH9fJKBRHGP5D4bD4sWpmSwMn52FMfXEA";
    private static final String BOOP_PROGRAM_ID = "boop8hVGQGqehUK2iVEMEnMrL5RbjywRzHKBmBE7ry4";
    private static final String RAYDIUM_PROGRAM_ID = "675kPX9MHTjS2zt1qfr1NYHuzeLXfQM9H24wFSUt1Mp8";
    private static final String RAYDIUM_CAMM_PROGRAM_ID = "CAMMCzo5YL8w4VFF8KVHrK22GGUsp5VTaW7grrKgrWqK";
    private static final String RAYDIUM_CPMM_PROGRAM_ID = "CPMMoo8L3F4NbTegBCKVNunggL7H1ZpdTHKxQB5qKP1C";
    private static final String RAYDIUM_LAUNCHPAD_PROGRAM_ID = "LanMV9sAd7wArD4vJFi2qDdfnVhFxYSUg6eADduJ3uj";
    private static final String METEORA_POOLS_PROGRAM_ID = "Eo7WjKq67rjJQSZxS6z3YkapzY3eMj6Xy8X5EQVn5UaB";
    private static final String SOL_MINT = "So11111111111111111111111111111111111111112";
    private static final double LAMPORTS_PER_SOL = 1_000_000_000.0;

    private static final Map<String, String> PLATFORM_MAP = Map.ofEntries(
            Map.entry(JUPITER_PROGRAM_ID, "Jupiter"),
            Map.entry(OKX_DEX_PROGRAM_ID, "OKX DEX"),
            Map.entry(PUMP_PROGRAM_ID, "Pump.fun"),
            Map.entry(PUMP_AMM_PROGRAM_ID, "Pump.fun"),
            Map.entry(BOOP_PROGRAM_ID, "Boop.fun"),
            Map.entry(RAYDIUM_PROGRAM_ID, "Raydium"),
            Map.entry(RAYDIUM_CAMM_PROGRAM_ID, "Raydium"),
            Map.entry(RAYDIUM_CPMM_PROGRAM_ID, "Raydium"),
            Map.entry(RAYDIUM_LAUNCHPAD_PROGRAM_ID, "Raydium"),
            Map.entry(METEORA_POOLS_PROGRAM_ID, "Meteora")
    );

    /**
     * 批量解析event，返回所有标准化交易记录
     */
    public List<ParsedTransactionDto> analyzeEvent(QuickNodeTransactionEventDto event) {
        List<ParsedTransactionDto> resultList = new java.util.ArrayList<>();
        if (event.getMatchedTransactions() == null || event.getMatchedTransactions().isEmpty()) {
            return resultList;
        }
        for (QuickNodeTransactionEventDto.MatchedTransaction tx : event.getMatchedTransactions()) {
            ParsedTransactionDto record = new ParsedTransactionDto();
            record.setSignature(tx.getSignature());
            record.setTimestamp(tx.getBlockTime());
            record.setSlot(tx.getSlot());
            record.setSignerAddress(tx.getTrackedAddress()); // trackedAddress is the signer we are interested in

            String programId = null;
            String platform = null;
            if (tx.getMatchedInstructions() != null && !tx.getMatchedInstructions().isEmpty()) {
                // Prefer programId from the first matched instruction, assuming it's most relevant
                programId = tx.getMatchedInstructions().get(0).getProgramId();
                for (Map.Entry<String, String> entry : PLATFORM_MAP.entrySet()) {
                    if (entry.getKey().equalsIgnoreCase(programId)) {
                        platform = entry.getValue();
                        break;
                    }
                }
            }
            record.setPlatform(platform);
            record.setProgramId(programId);

            String trackedAddress = tx.getTrackedAddress();
            if (Objects.isNull(trackedAddress)) {
                log.warn("Skipping transaction analysis due to null trackedAddress for signature: {}", tx.getSignature());
                continue;
            }
            
            List<QuickNodeTransactionEventDto.TokenBalance> preTokenBalances = tx.getPreTokenBalances() == null ? java.util.Collections.emptyList() : tx.getPreTokenBalances();
            List<QuickNodeTransactionEventDto.TokenBalance> postTokenBalances = tx.getPostTokenBalances() == null ? java.util.Collections.emptyList() : tx.getPostTokenBalances();
            
            Map<String, Double> preMap = new java.util.HashMap<>();
            Map<String, Double> postMap = new java.util.HashMap<>();

            for (QuickNodeTransactionEventDto.TokenBalance pre : preTokenBalances) {
                if (trackedAddress.equals(pre.getOwner()) && pre.getUiTokenAmount() != null && pre.getUiTokenAmount().getUiAmount() != null) {
                    preMap.put(pre.getMint(), pre.getUiTokenAmount().getUiAmount());
                }
            }
            for (QuickNodeTransactionEventDto.TokenBalance post : postTokenBalances) {
                if (trackedAddress.equals(post.getOwner()) && post.getUiTokenAmount() != null && post.getUiTokenAmount().getUiAmount() != null) {
                    postMap.put(post.getMint(), post.getUiTokenAmount().getUiAmount());
                }
            }

            Map<String, Double> diffMap = new java.util.HashMap<>();
            java.util.Set<String> allMints = new java.util.HashSet<>(preMap.keySet());
            allMints.addAll(postMap.keySet());

            for (String mint : allMints) {
                double preAmt = preMap.getOrDefault(mint, 0.0);
                double postAmt = postMap.getOrDefault(mint, 0.0);
                double diff = postAmt - preAmt;
                if (Math.abs(diff) > 1e-9) {
                    diffMap.put(mint, diff);
                }
            }
            log.debug("Tx: {}, Tracked: {}, preMap: {}, postMap: {}, diffMap: {}", tx.getSignature(), trackedAddress, preMap, postMap, diffMap);

            String tokenInputMint = null, tokenOutputMint = null;
            Double tokenInputAmount = null, tokenOutputAmount = null;

            List<Map.Entry<String, Double>> splTokenInputs = diffMap.entrySet().stream()
                .filter(e -> e.getValue() < 0).collect(Collectors.toList());
            List<Map.Entry<String, Double>> splTokenOutputs = diffMap.entrySet().stream()
                .filter(e -> e.getValue() > 0).collect(Collectors.toList());

            if (splTokenInputs.size() == 1 && splTokenOutputs.size() == 1) {
                // Scenario 1: SPL-to-SPL swap
                tokenInputMint = splTokenInputs.get(0).getKey();
                tokenInputAmount = -splTokenInputs.get(0).getValue();
                tokenOutputMint = splTokenOutputs.get(0).getKey();
                tokenOutputAmount = splTokenOutputs.get(0).getValue();
                log.debug("SPL-SPL swap for {}: In {} ({}), Out {} ({})", trackedAddress, tokenInputMint, tokenInputAmount, tokenOutputMint, tokenOutputAmount);
            } else if (diffMap.size() == 1) {
                // Scenario 2: SPL vs SOL swap
                Map.Entry<String, Double> splEntry = diffMap.entrySet().iterator().next();
                String splMint = splEntry.getKey();
                double splAmountChange = splEntry.getValue();

                if (tx.getPreBalances() != null && !tx.getPreBalances().isEmpty() &&
                    tx.getPostBalances() != null && !tx.getPostBalances().isEmpty()) {
                    
                    long preSolLamports = tx.getPreBalances().get(0); // Assuming index 0 is for the trackedAddress or primary signer
                    long postSolLamports = tx.getPostBalances().get(0);

                    if (splAmountChange > 0) { // SOL -> SPL
                        tokenOutputMint = splMint;
                        tokenOutputAmount = splAmountChange;
                        tokenInputMint = SOL_MINT;
                        double solSpent = (preSolLamports - postSolLamports) / LAMPORTS_PER_SOL;
                        if (solSpent > 1e-9) {
                           tokenInputAmount = solSpent;
                        }
                    } else { // SPL -> SOL
                        tokenInputMint = splMint;
                        tokenInputAmount = -splAmountChange;
                        tokenOutputMint = SOL_MINT;
                        double solReceived = (postSolLamports - preSolLamports) / LAMPORTS_PER_SOL;
                        if (solReceived > 1e-9) {
                            tokenOutputAmount = solReceived;
                        }
                    }
                } else {
                    log.warn("Missing preBalances or postBalances for SOL calculation. Sig: {}, Tracked: {}", tx.getSignature(), trackedAddress);
                }
            }

            record.setTokenInputMint(tokenInputMint);
            record.setTokenInputAmount(tokenInputAmount);
            record.setTokenOutputMint(tokenOutputMint);
            record.setTokenOutputAmount(tokenOutputAmount);
            
            // Only add if there's some token movement identified (either input or output mint is not null)
            if (tokenInputMint != null || tokenOutputMint != null) {
                 resultList.add(record);
            }
        }
        return resultList;
    }
} 