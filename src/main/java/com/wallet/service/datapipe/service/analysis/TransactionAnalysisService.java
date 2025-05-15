package com.wallet.service.datapipe.service.analysis;

import com.wallet.service.datapipe.dto.AnalyzedWalletAction;
import com.wallet.service.datapipe.dto.AnalyzedWalletAction.AnalyzedWalletActionBuilder;
import com.wallet.service.datapipe.dto.ParsedTransactionDto;
import com.wallet.service.datapipe.dto.quicknode.QuickNodeTransactionEventDto;
import com.wallet.service.datapipe.dto.rpc.SolanaTransactionResponseDto;
import com.wallet.service.utils.StringFormatUtils;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
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
    private static final String PHOTON_PROGRAM_ID = "BSfD6SHZigAfDWSjzD5Q41jw8LmKwtmjskPH9XW1mrRW";
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
            Map.entry(METEORA_POOLS_PROGRAM_ID, "Meteora"),
            Map.entry(PHOTON_PROGRAM_ID, "Photon")
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
            record.setSignerAddress(tx.getTrackedAddress());

            String programId = null;
            String platform = null;
            if (tx.getMatchedInstructions() != null && !tx.getMatchedInstructions().isEmpty()) {
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
                log.warn("因 trackedAddress 为空，跳过交易 {} 的分析。", (tx.getSignature() == null ? "N/A" : StringFormatUtils.formatAddress(tx.getSignature())));
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
            log.debug("原始 DiffMap - 交易: {}, 跟踪地址: {}, diffMap: {}", 
                (tx.getSignature() == null ? "N/A" : StringFormatUtils.formatAddress(tx.getSignature())), 
                StringFormatUtils.formatAddress(trackedAddress), 
                diffMap);

            // Calculate SOL lamports change directly from pre/post Balances
            long solLamportsChange = 0;
            if (tx.getPreBalances() != null && !tx.getPreBalances().isEmpty() &&
                tx.getPostBalances() != null && !tx.getPostBalances().isEmpty()) {
                // Assuming index 0 of preBalances/postBalances corresponds to the trackedAddress if it's the fee payer or primary account.
                // This assumption is based on typical transaction structures but might need validation against QuickNode's specific guarantees for `trackedAddress` context.
                if (tx.getPreBalances().size() > 0 && tx.getPostBalances().size() > 0) { // Ensure lists are not empty
                     // TODO: Verify if index 0 is always correct for the trackedAddress SOL balance.
                     // For now, we assume the first entry in balances corresponds to the main account affected (often the fee payer).
                     // If QuickNode stream provides account index for trackedAddress, that would be more robust.
                    solLamportsChange = tx.getPostBalances().get(0) - tx.getPreBalances().get(0);
                }
            }
            double solAmountNetChange = solLamportsChange / LAMPORTS_PER_SOL;

            // Filter out SOL from diffMap to get splDiffMap, as SOL is handled separately via solAmountNetChange
            Map<String, Double> splDiffMap = diffMap.entrySet().stream()
                .filter(entry -> !SOL_MINT.equals(entry.getKey()))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
            log.debug("SPL DiffMap - 交易: {}, 跟踪地址: {}, splDiffMap: {}, SOL净变动: {}", 
                (tx.getSignature() == null ? "N/A" : StringFormatUtils.formatAddress(tx.getSignature())), 
                StringFormatUtils.formatAddress(trackedAddress), 
                splDiffMap, 
                solAmountNetChange);

            // If only SOL balance changed (no SPL token changes), skip this transaction event for this wallet
            if (splDiffMap.isEmpty() && Math.abs(solAmountNetChange) > 1e-9) {
                log.info("交易 {} (跟踪地址 {}): 仅发生SOL余额变动 ({} SOL)，疑似套利交易。跳过此交易事件的解析。", 
                    (tx.getSignature() == null ? "N/A" : StringFormatUtils.formatAddress(tx.getSignature())), 
                    StringFormatUtils.formatAddress(trackedAddress), 
                    solAmountNetChange);
                continue;
            }

            String tokenInputMint = null, tokenOutputMint = null;
            Double tokenInputAmount = null, tokenOutputAmount = null;

            // Use splDiffMap for identifying SPL token inputs and outputs
            List<Map.Entry<String, Double>> splTokenInputs = splDiffMap.entrySet().stream()
                .filter(e -> e.getValue() < 0).collect(Collectors.toList());
            List<Map.Entry<String, Double>> splTokenOutputs = splDiffMap.entrySet().stream()
                .filter(e -> e.getValue() > 0).collect(Collectors.toList());

            if (splTokenInputs.size() == 1 && splTokenOutputs.size() == 1) {
                // Scenario 1: SPL-to-SPL swap (neither is SOL)
                tokenInputMint = splTokenInputs.get(0).getKey();
                tokenInputAmount = -splTokenInputs.get(0).getValue();
                tokenOutputMint = splTokenOutputs.get(0).getKey();
                tokenOutputAmount = splTokenOutputs.get(0).getValue();
                log.debug("钱包 {} 的SPL-SPL交换: 输入 {} ({}), 输出 {} ({})", 
                    StringFormatUtils.formatAddress(trackedAddress), 
                    (tokenInputMint == null ? "null" : StringFormatUtils.formatAddress(tokenInputMint)), tokenInputAmount, 
                    (tokenOutputMint == null ? "null" : StringFormatUtils.formatAddress(tokenOutputMint)), tokenOutputAmount);
            } else if (splDiffMap.size() == 1 && Math.abs(solAmountNetChange) > 1e-9) { 
                // Scenario 2: One SPL token vs SOL swap
                Map.Entry<String, Double> splEntry = splDiffMap.entrySet().iterator().next();
                String splMint = splEntry.getKey();
                double splAmountChange = splEntry.getValue(); // Change in SPL token for the wallet

                if (splAmountChange > 0) { // Wallet received SPL token, so SOL must have been spent
                    tokenOutputMint = splMint;
                    tokenOutputAmount = splAmountChange;
                    tokenInputMint = SOL_MINT;
                    tokenInputAmount = -solAmountNetChange; // SOL spent, so solAmountNetChange is negative
                } else { // Wallet sent SPL token, so SOL must have been received
                    tokenInputMint = splMint;
                    tokenInputAmount = -splAmountChange;
                    tokenOutputMint = SOL_MINT;
                    tokenOutputAmount = solAmountNetChange; // SOL received, so solAmountNetChange is positive
                }
                log.debug("钱包 {} 的SPL-SOL交换: SPL {} ({}), SOL {} ({})", 
                    StringFormatUtils.formatAddress(trackedAddress), 
                    (tokenInputMint == null ? "null" : StringFormatUtils.formatAddress(tokenInputMint)), tokenInputAmount, 
                    (tokenOutputMint == null ? "null" : StringFormatUtils.formatAddress(tokenOutputMint)), tokenOutputAmount);
            } else if (splDiffMap.isEmpty() && Math.abs(solAmountNetChange) < 1e-9) {
                 // This case means no SPL changes AND no significant SOL changes. This could be a failed transaction or irrelevant interaction.
                 log.debug("交易 {} (跟踪地址 {}): 无SPL代币变动且SOL变动可忽略。可能为失败或不相关的交易。", 
                    (tx.getSignature() == null ? "N/A" : StringFormatUtils.formatAddress(tx.getSignature())), 
                    StringFormatUtils.formatAddress(trackedAddress));
                 // continue; // Already handled by the SOL-only check if solAmountNetChange was significant.
                           // If both are negligible, tokenInput/OutputMint will remain null, and it won't be added.
            } else if (splDiffMap.size() > 0){
                 // Complex scenario or simple transfer (not a swap against SOL or another SPL)
                 // For example, just sending or receiving one or more SPL tokens without a clear counterpart in this event structure.
                 // We'll represent the largest change if multiple, or just one if only one token changed.
                 // This part of logic might need refinement based on desired output for non-swap SPL transfers.
                 if (splTokenOutputs.size() >= 1) { // Prioritize what was received
                    tokenOutputMint = splTokenOutputs.get(0).getKey();
                    tokenOutputAmount = splTokenOutputs.get(0).getValue();
                 }
                 if (splTokenInputs.size() >= 1) { // Then what was sent
                    tokenInputMint = splTokenInputs.get(0).getKey();
                    tokenInputAmount = -splTokenInputs.get(0).getValue();
                 }
                 log.debug("交易 {} (跟踪地址 {}): 复杂或简单的SPL代币转移。输入: {} {}, 输出: {} {}", 
                    (tx.getSignature() == null ? "N/A" : StringFormatUtils.formatAddress(tx.getSignature())), 
                    StringFormatUtils.formatAddress(trackedAddress), 
                    tokenInputAmount, (tokenInputMint == null ? "null" : StringFormatUtils.formatAddress(tokenInputMint)),
                    tokenOutputAmount, (tokenOutputMint == null ? "null" : StringFormatUtils.formatAddress(tokenOutputMint)));
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

    /**
     * Analyzes a single transaction obtained from RPC (e.g., getTransaction) to identify actions
     * relevant to the specified walletAddress.
     *
     * @param transactionResponse The full transaction details from a getTransaction RPC call.
     * @param walletAddress The wallet address to focus the analysis on.
     * @return A list of AnalyzedWalletAction representing token movements for the given wallet.
     */
    public List<AnalyzedWalletAction> analyzeRpcTransactionForWalletActions(
            SolanaTransactionResponseDto transactionResponse,
            String walletAddress) {
        
        List<AnalyzedWalletAction> actions = new java.util.ArrayList<>();

        if (transactionResponse == null || transactionResponse.getResult() == null) {
            log.warn("无法分析RPC交易：transactionResponse 或其 result 为空。");
            return actions;
        }

        SolanaTransactionResponseDto.SolanaRpcResult rpcResult = transactionResponse.getResult();
        if (rpcResult.getMeta() == null || rpcResult.getTransaction() == null || rpcResult.getTransaction().getMessage() == null) {
            log.warn("无法分析RPC交易：meta、transaction 或 message 为空。");
            return actions;
        }

        String signature = !rpcResult.getTransaction().getSignatures().isEmpty() ? rpcResult.getTransaction().getSignatures().get(0) : "N/A";
        SolanaTransactionResponseDto.TransactionMetaDto meta = rpcResult.getMeta();
        SolanaTransactionResponseDto.ParsedMessageDto message = rpcResult.getTransaction().getMessage();
        Long blockTime = rpcResult.getBlockTime();
        Long slot = rpcResult.getSlot();

        if (meta.getErr() != null) {
            log.debug("交易 {} 执行失败，不为钱包 {} 分析操作。错误: {}", 
                StringFormatUtils.formatAddress(signature), 
                StringFormatUtils.formatAddress(walletAddress), 
                meta.getErr());
            return actions;
        }

        String primaryProgramId = null;
        String platform = null;
        if (message.getInstructions() != null && !message.getInstructions().isEmpty()) {
            for (SolanaTransactionResponseDto.ParsedInstructionDto instruction : message.getInstructions()) {
                String currentProgramId = instruction.getProgramId();
                if (PLATFORM_MAP.containsKey(currentProgramId)) {
                    primaryProgramId = currentProgramId;
                    platform = PLATFORM_MAP.get(currentProgramId);
                    break;
                }
            }
        }
        final String finalPlatform = platform;
        final String finalProgramId = primaryProgramId;

        Map<String, Double> preMap = new java.util.HashMap<>();
        Map<String, Double> postMap = new java.util.HashMap<>();

        List<SolanaTransactionResponseDto.TokenBalanceDto> preTokenBalances = meta.getPreTokenBalances() == null ? java.util.Collections.emptyList() : meta.getPreTokenBalances();
        List<SolanaTransactionResponseDto.TokenBalanceDto> postTokenBalances = meta.getPostTokenBalances() == null ? java.util.Collections.emptyList() : meta.getPostTokenBalances();

        for (SolanaTransactionResponseDto.TokenBalanceDto pre : preTokenBalances) {
            if (walletAddress.equals(pre.getOwner()) && pre.getUiTokenAmount() != null && pre.getUiTokenAmount().getUiAmount() != null) {
                preMap.put(pre.getMint(), pre.getUiTokenAmount().getUiAmount());
            }
        }
        for (SolanaTransactionResponseDto.TokenBalanceDto post : postTokenBalances) {
            if (walletAddress.equals(post.getOwner()) && post.getUiTokenAmount() != null && post.getUiTokenAmount().getUiAmount() != null) {
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
        log.debug("RPC交易: {}, 钱包: {}, 原始代币余额变动 (diffMap): {}", 
            StringFormatUtils.formatAddress(signature), 
            StringFormatUtils.formatAddress(walletAddress), 
            diffMap);

        int walletAccountIndex = -1;
        List<SolanaTransactionResponseDto.AccountKeyDto> accountKeys = message.getAccountKeys();
        for (int i = 0; i < accountKeys.size(); i++) {
            if (walletAddress.equals(accountKeys.get(i).getPubkey())) {
                walletAccountIndex = i;
                break;
            }
        }
        long preSolLamports = 0;
        long postSolLamports = 0;
        boolean solBalancesAvailable = false;
        if (walletAccountIndex != -1 && meta.getPreBalances() != null && meta.getPostBalances() != null &&
            meta.getPreBalances().size() > walletAccountIndex && meta.getPostBalances().size() > walletAccountIndex) {
            preSolLamports = meta.getPreBalances().get(walletAccountIndex);
            postSolLamports = meta.getPostBalances().get(walletAccountIndex);
            solBalancesAvailable = true;
        }
        double solAmountChange = solBalancesAvailable ? (postSolLamports - preSolLamports) / LAMPORTS_PER_SOL : 0.0;
        log.debug("RPC交易: {}, 钱包: {}, SOL余额变动: {}", 
            StringFormatUtils.formatAddress(signature), 
            StringFormatUtils.formatAddress(walletAddress), 
            solAmountChange);

        List<Map.Entry<String, Double>> splTokenChanges = diffMap.entrySet().stream()
            .filter(e -> !SOL_MINT.equals(e.getKey())) // Exclude SOL from SPL changes, handle it via solAmountChange
            .collect(Collectors.toList());

        AnalyzedWalletActionBuilder actionBuilder = AnalyzedWalletAction.builder()
            .transactionSignature(signature)
            .walletAddress(walletAddress)
            .platform(finalPlatform)
            .programId(finalProgramId)
            .timestamp(blockTime)
            .slot(slot);

        boolean actionSet = false;

        if (splTokenChanges.isEmpty()) {
            if (Math.abs(solAmountChange) > 1e-9) { 
                if (solAmountChange < 0) { 
                    actionBuilder.tokenInMint(SOL_MINT).tokenInAmount(BigDecimal.valueOf(Math.abs(solAmountChange)));
                } else { 
                    actionBuilder.tokenOutMint(SOL_MINT).tokenOutAmount(BigDecimal.valueOf(solAmountChange));
                }
                log.debug("RPC交易: {}, 钱包: {}, 仅发生SOL余额变动: {}。判定为SOL转账或相关操作。", 
                    StringFormatUtils.formatAddress(signature), 
                    StringFormatUtils.formatAddress(walletAddress), 
                    solAmountChange);
                actionSet = true;
            } else {
                log.debug("RPC交易: {}, 钱包: {}, 无SPL代币余额变动且SOL变动可忽略。不做处理。", 
                    StringFormatUtils.formatAddress(signature), 
                    StringFormatUtils.formatAddress(walletAddress));
                return actions; 
            }
        } else if (splTokenChanges.size() == 1) {
            Map.Entry<String, Double> splEntry = splTokenChanges.get(0);
            String splMint = splEntry.getKey();
            double splChangeValue = splEntry.getValue(); 
            BigDecimal splAbsAmount = BigDecimal.valueOf(Math.abs(splChangeValue));

            if (splChangeValue > 0) { 
                actionBuilder.tokenOutMint(splMint).tokenOutAmount(splAbsAmount);
                if (solAmountChange < -1e-9) { 
                    actionBuilder.tokenInMint(SOL_MINT).tokenInAmount(BigDecimal.valueOf(Math.abs(solAmountChange)));
                }
            } else { 
                actionBuilder.tokenInMint(splMint).tokenInAmount(splAbsAmount);
                if (solAmountChange > 1e-9) { 
                    actionBuilder.tokenOutMint(SOL_MINT).tokenOutAmount(BigDecimal.valueOf(solAmountChange));
                }
            }
            log.debug("RPC交易: {}, 钱包: {}, 单一SPL ({}) 变动: {}. SOL变动: {}.", 
                      StringFormatUtils.formatAddress(signature), 
                      StringFormatUtils.formatAddress(walletAddress), 
                      (splMint == null ? "null" : StringFormatUtils.formatAddress(splMint)), 
                      splChangeValue, 
                      solAmountChange);
            actionSet = true;
        } else if (splTokenChanges.size() == 2) {
            Map.Entry<String, Double> entry1 = splTokenChanges.get(0);
            Map.Entry<String, Double> entry2 = splTokenChanges.get(1);

            if (entry1.getValue() < 0 && entry2.getValue() > 0) { 
                actionBuilder.tokenInMint(entry1.getKey()).tokenInAmount(BigDecimal.valueOf(Math.abs(entry1.getValue())));
                actionBuilder.tokenOutMint(entry2.getKey()).tokenOutAmount(BigDecimal.valueOf(entry2.getValue()));
                actionSet = true;
            } else if (entry1.getValue() > 0 && entry2.getValue() < 0) { 
                actionBuilder.tokenOutMint(entry1.getKey()).tokenOutAmount(BigDecimal.valueOf(entry1.getValue()));
                actionBuilder.tokenInMint(entry2.getKey()).tokenInAmount(BigDecimal.valueOf(Math.abs(entry2.getValue())));
                actionSet = true;
            } else {
                log.warn("RPC交易: {}, 钱包: {}, 两SPL代币变动方向异常 (非标准买卖)。 Diff1: {} {}, Diff2: {} {}.", 
                         StringFormatUtils.formatAddress(signature), 
                         StringFormatUtils.formatAddress(walletAddress), 
                         (entry1.getKey() == null ? "null" : StringFormatUtils.formatAddress(entry1.getKey())), entry1.getValue(), 
                         (entry2.getKey() == null ? "null" : StringFormatUtils.formatAddress(entry2.getKey())), entry2.getValue());
            }
            if(actionSet) {
                 log.debug("RPC交易: {}, 钱包: {}, SPL-SPL Swap/相关操作确定。", 
                    StringFormatUtils.formatAddress(signature), 
                    StringFormatUtils.formatAddress(walletAddress));
            }
        } else {
            log.warn("RPC交易: {}, 钱包: {}, 发现 >2 条SPL代币 ({}) 变动，操作复杂。当前逻辑不处理此情况。SPL变动: {}", 
                     StringFormatUtils.formatAddress(signature), 
                     StringFormatUtils.formatAddress(walletAddress), 
                     splTokenChanges.size(), 
                     splTokenChanges);
        }

        if (actionSet) {
            AnalyzedWalletAction builtAction = actionBuilder.build();
            if (builtAction.getTokenInMint() != null || builtAction.getTokenOutMint() != null) {
                 actions.add(builtAction);
            } else {
                log.warn("RPC交易: {}, 钱包: {}, actionSet为true但未填充tokenInMint或tokenOutMint。异常情况，不添加action。", 
                    StringFormatUtils.formatAddress(signature), 
                    StringFormatUtils.formatAddress(walletAddress));
            }
        } else if (!splTokenChanges.isEmpty()) { 
             log.warn("RPC交易: {}, 钱包: {}, 有SPL代币变动但未能解析为明确的 tokenIn/tokenOut 操作。SPL变动: {}, SOL变动: {}", 
                     StringFormatUtils.formatAddress(signature), 
                     StringFormatUtils.formatAddress(walletAddress), 
                     splTokenChanges, 
                     solAmountChange);
        }
        
        return actions;
    }
} 