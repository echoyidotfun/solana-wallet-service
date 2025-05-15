package com.wallet.service.datapipe.service.processor;

import com.wallet.service.datapipe.client.quicknode.http.SolanaRpcService;
import com.wallet.service.datapipe.dto.AnalyzedWalletAction;
import com.wallet.service.datapipe.dto.quicknode.wss.LogsNotificationDto;
import com.wallet.service.datapipe.dto.rpc.SolanaTransactionResponseDto;
import com.wallet.service.datapipe.model.Token;
import com.wallet.service.datapipe.repository.TokenRepository;
import com.wallet.service.datapipe.service.RoomRealtimeUpdateService;
import com.wallet.service.datapipe.service.analysis.TransactionAnalysisService;
import com.wallet.service.utils.StringFormatUtils;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@Slf4j
@RequiredArgsConstructor
public class RealtimeTransactionProcessorServiceImpl implements RealtimeTransactionProcessorService {

    private final TokenRepository tokenRepository;
    private final SolanaRpcService solanaRpcService;
    private final TransactionAnalysisService transactionAnalysisService;
    private final RoomRealtimeUpdateService roomRealtimeUpdateService;

    private static final String SOL_MINT_ADDRESS = "So11111111111111111111111111111111111111112";

    // Target program IDs for pre-filtering relevant transactions
    private static final List<String> TARGET_PROGRAM_IDS = List.of(
            "JUP6LkbZbjS1jKKwapdHNy74zcZ3tLUZoi5QNyVTaV4", // Jupiter
            "6m2CDdhRgxpH4WjvdzxAYbGxwdGUz5MziiL5jek2kBma", // OKX_DEX
            "6EF8rrecthR5Dkzon8Nwu78hRvfCKubJ14M5uBEwF6P", // PUMP
            "pAMMBay6oceH9fJKBRHGP5D4bD4sWpmSwMn52FMfXEA", // PUMP_AMM
            "boop8hVGQGqehUK2iVEMEnMrL5RbjywRzHKBmBE7ry4", // BOOP
            "675kPX9MHTjS2zt1qfr1NYHuzeLXfQM9H24wFSUt1Mp8", // RAYDIUM
            "CAMMCzo5YL8w4VFF8KVHrK22GGUsp5VTaW7grrKgrWqK", // RAYDIUM_CAMM
            "CPMMoo8L3F4NbTegBCKVNunggL7H1ZpdTHKxQB5qKP1C", // RAYDIUM_CPMM
            "LanMV9sAd7wArD4vJFi2qDdfnVhFxYSUg6eADduJ3uj", // RAYDIUM_LAUNCHPAD
            "Eo7WjKq67rjJQSZxS6z3YkapzY3eMj6Xy8X5EQVn5UaB"  // METEORA_POOLS
    );

    // Extracted from smart_money_filter.js
    private static final class ProgramIdConstants {
        static final String JUPITER = "JUP6LkbZbjS1jKKwapdHNy74zcZ3tLUZoi5QNyVTaV4";
        static final String OKX_DEX = "6m2CDdhRgxpH4WjvdzxAYbGxwdGUz5MziiL5jek2kBma";
        static final String PUMP = "6EF8rrecthR5Dkzon8Nwu78hRvfCKubJ14M5uBEwF6P";
        static final String PUMP_AMM = "pAMMBay6oceH9fJKBRHGP5D4bD4sWpmSwMn52FMfXEA"; // Often used with Pump
        static final String BOOP = "boop8hVGQGqehUK2iVEMEnMrL5RbjywRzHKBmBE7ry4";
        static final String RAYDIUM = "675kPX9MHTjS2zt1qfr1NYHuzeLXfQM9H24wFSUt1Mp8";
        static final String RAYDIUM_CAMM = "CAMMCzo5YL8w4VFF8KVHrK22GGUsp5VTaW7grrKgrWqK";
        static final String RAYDIUM_CPMM = "CPMMoo8L3F4NbTegBCKVNunggL7H1ZpdTHKxQB5qKP1C";
        static final String RAYDIUM_LAUNCHPAD = "LanMV9sAd7wArD4vJFi2qDdfnVhFxYSUg6eADduJ3uj";
        static final String METEORA_POOLS = "Eo7WjKq67rjJQSZxS6z3YkapzY3eMj6Xy8X5EQVn5UaB";
    }

    // Keywords derived from IX_DISCRIMINATORS in smart_money_filter.js
    // We will look for "Instruction: <Keyword>" or the keyword itself in logs.
    private static final Map<String, List<String>> TARGET_INSTRUCTION_KEYWORDS = Map.ofEntries(
            Map.entry(ProgramIdConstants.JUPITER, Arrays.asList("route", "SharedAccountsExactOutRoute", "SharedAccountsRoute", "Swap")), // Jupiter logs can also contain "Swap" for certain instructions
            Map.entry(ProgramIdConstants.OKX_DEX, Arrays.asList("Swap2")), // "Swap2" is the key, "Swap" is common log term
            Map.entry(ProgramIdConstants.PUMP, Arrays.asList("Buy", "Sell")),
            Map.entry(ProgramIdConstants.PUMP_AMM, Arrays.asList("Buy", "Sell")), // Associated with Pump
            Map.entry(ProgramIdConstants.BOOP, Arrays.asList("SellToken", "BuyToken")),
            Map.entry(ProgramIdConstants.RAYDIUM, Arrays.asList("Swap")),
            Map.entry(ProgramIdConstants.RAYDIUM_CAMM, Arrays.asList("SwapV2")),
            Map.entry(ProgramIdConstants.RAYDIUM_CPMM, Arrays.asList("SwapBaseInput", "SwapBaseOutput")),
            Map.entry(ProgramIdConstants.RAYDIUM_LAUNCHPAD, Arrays.asList("BuyExactIn", "SellExactIn")),
            Map.entry(ProgramIdConstants.METEORA_POOLS, Arrays.asList("Swap"))
    );


    @Override
    public void processLogNotification(LogsNotificationDto.ValueDto logValue, String walletAddress, List<RoomTarget> relevantRoomTargets) {
        if (logValue == null || CollectionUtils.isEmpty(logValue.getLogs()) || CollectionUtils.isEmpty(relevantRoomTargets)) {
            log.debug("跳过处理钱包 {} 的日志: logValue、其日志或 relevantRoomTargets 为空。交易签名: {}",
                    StringFormatUtils.formatAddress(walletAddress),
                    logValue != null ? StringFormatUtils.formatAddress(logValue.getSignature()) : "N/A");
            return;
        }

        if (!isTransactionLikelyRelevant(logValue.getLogs(), walletAddress, logValue.getSignature())) {
            log.debug("钱包 {} (交易签名: {}) 的交易日志被预检认为不相关。",
                    StringFormatUtils.formatAddress(walletAddress),
                    StringFormatUtils.formatAddress(logValue.getSignature()));
            return;
        }

        String signature = logValue.getSignature();
        log.info("发现钱包 {} (交易签名: {}) 的相关日志。正在获取交易详情。",
                StringFormatUtils.formatAddress(walletAddress),
                StringFormatUtils.formatAddress(signature));
        Optional<SolanaTransactionResponseDto> transactionResponseOptional = solanaRpcService.getTransactionDetails(signature);

        if (transactionResponseOptional.isEmpty()) {
            log.warn("获取交易 {} (钱包: {}) 的详情失败。",
                    StringFormatUtils.formatAddress(signature),
                    StringFormatUtils.formatAddress(walletAddress));
            return;
        }
        SolanaTransactionResponseDto transactionResponse = transactionResponseOptional.get();

        if (transactionResponse.getResult() == null || (transactionResponse.getResult().getMeta() != null && transactionResponse.getResult().getMeta().getErr() != null)) {
            log.debug("钱包 {} 的交易 {} 执行失败或结果无效，跳过分析。错误: {}",
                    StringFormatUtils.formatAddress(walletAddress),
                    StringFormatUtils.formatAddress(signature),
                    (transactionResponse.getResult() != null && transactionResponse.getResult().getMeta() != null ? transactionResponse.getResult().getMeta().getErr() : "结果为空"));
            return;
        }

        List<AnalyzedWalletAction> actions = transactionAnalysisService.analyzeRpcTransactionForWalletActions(transactionResponse, walletAddress);

        if (actions.isEmpty()) {
            log.debug("未在交易 {} 中找到钱包 {} 的相关操作。",
                    StringFormatUtils.formatAddress(walletAddress),
                    StringFormatUtils.formatAddress(signature));
            return;
        }

        for (AnalyzedWalletAction action : actions) {
            String formattedWalletAddress = StringFormatUtils.formatAddress(walletAddress);
            
            for (RoomTarget roomTarget : relevantRoomTargets) {
                boolean tokenInIsTarget = action.getTokenInMint() != null && action.getTokenInMint().equals(roomTarget.getTargetTokenAddress());
                boolean tokenOutIsTarget = action.getTokenOutMint() != null && action.getTokenOutMint().equals(roomTarget.getTargetTokenAddress());

                // if (tokenInIsTarget || tokenOutIsTarget) {
                    String tokenInInfo = "";
                    if (StringUtils.hasText(action.getTokenInMint()) && action.getTokenInAmount() != null) {
                        tokenInInfo = getTokenDisplayInfo(action.getTokenInMint(), action.getTokenInAmount(), false);
                    }

                    String tokenOutInfo = "";
                    if (StringUtils.hasText(action.getTokenOutMint()) && action.getTokenOutAmount() != null) {
                        tokenOutInfo = getTokenDisplayInfo(action.getTokenOutMint(), action.getTokenOutAmount(), false);
                    }
                    
                    String targetTokenDisplay = getTokenDisplayInfo(roomTarget.getTargetTokenAddress(), null, true);

                    String details;
                    if (StringUtils.hasText(tokenInInfo) && StringUtils.hasText(tokenOutInfo)) {
                        details = String.format("[%s] %s -> %s", action.getPlatform(), tokenInInfo, tokenOutInfo);
                    } else if (StringUtils.hasText(tokenInInfo)) {
                        details = String.format("[%s] 付出 %s", action.getPlatform(), tokenInInfo);
                    } else if (StringUtils.hasText(tokenOutInfo)) {
                        details = String.format("[%s] 收到 %s", action.getPlatform(), tokenOutInfo);
                    } else {
                        details = "无明确代币流动信息";
                    }

                    log.info("钱包 {} 在房间 {} (目标代币 {}): {}",
                            formattedWalletAddress,
                            roomTarget.getRoomId(), 
                            targetTokenDisplay,
                            details.trim());

                    // roomRealtimeUpdateService.sendActionUpdate(roomTarget.getRoomId(), action); 
                // }
            }
        }
    }

    // symbolOnly: if true, only returns SYMBOL(MINT_ADDRESS_SHORT), amount is ignored.
    // if false, returns FORMATTED_AMOUNT SYMBOL(MINT_ADDRESS_SHORT)
    private String getTokenDisplayInfo(String mintAddress, BigDecimal amount, boolean symbolOnly) {
        if (!StringUtils.hasText(mintAddress)) { 
            return ""; 
        }

        String symbol;
        String formattedMintAddress = StringFormatUtils.formatAddress(mintAddress);

        if (SOL_MINT_ADDRESS.equals(mintAddress)) {
            symbol = "SOL";
        } else {
            Optional<Token> tokenOptional = tokenRepository.findByMintAddress(mintAddress);
            symbol = tokenOptional.map(Token::getSymbol).orElse("未知"); 
        }
        
        if (symbolOnly) {
            return String.format("%s(%s)", symbol, formattedMintAddress);
        }

        String formattedAmount = "0.000000"; 
        if (amount != null) { 
            // Scale to 6 decimal places for display, using HALF_UP rounding
            formattedAmount = amount.setScale(6, RoundingMode.HALF_UP).toPlainString();
        } else {
             // if amount is null for a non-symbolOnly call, it's an issue, but display something.
            formattedAmount = "[数量未知]";
        }

        return String.format("%s %s(%s)", formattedAmount, symbol, formattedMintAddress).trim();
    }

    /**
     * Pre-checks logs to see if a full getTransaction call is warranted.
     * Checks for invocation of target program IDs AND relevant instruction keywords.
     * Updated logic: Program ID on one line, instruction keyword expected on the next line.
     */
    private boolean isTransactionLikelyRelevant(List<String> logEntries, String walletAddress, String signature) {
        String formattedWalletAddress = StringFormatUtils.formatAddress(walletAddress);
        String formattedSignature = StringFormatUtils.formatAddress(signature);

        if (CollectionUtils.isEmpty(logEntries)) {
            log.trace("钱包 {}: 预检时日志为空。交易签名: {}", formattedWalletAddress, formattedSignature);
            return false;
        }

        for (int i = 0; i < logEntries.size(); i++) {
            String currentLogEntry = logEntries.get(i);
            if (currentLogEntry == null || currentLogEntry.length() < 10) continue; // Basic sanity check

            for (String targetProgramId : TARGET_PROGRAM_IDS) {
                if (currentLogEntry.contains(targetProgramId)) {
                    // Program ID found on the current line.
                    // log.debug("Wallet {}: Log for sig {} matched program {} on line {}. Checking next line for keywords. Log: \'{}\'", formattedWalletAddress, formattedSignature, targetProgramId, i, currentLogEntry);

                    List<String> keywords = TARGET_INSTRUCTION_KEYWORDS.getOrDefault(targetProgramId, Collections.emptyList());

                    // If this program has no specific keywords we are looking for, a match on programId alone is sufficient.
                    if (keywords.isEmpty()) {
                        log.debug("钱包 {}: 交易 {} 的日志匹配程序 {} (无特定指令关键词，视为相关)。日志: \'{}\'", formattedWalletAddress, formattedSignature, targetProgramId, currentLogEntry);
                        return true;
                    }

                    // Check the next line for keywords
                    if (i + 1 < logEntries.size()) {
                        String nextLogEntry = logEntries.get(i + 1);
                        if (nextLogEntry != null) {
                            for (String keyword : keywords) {
                                if (nextLogEntry.toLowerCase().contains("instruction: " + keyword.toLowerCase()) ||
                                    nextLogEntry.toLowerCase().contains(keyword.toLowerCase())) {
                                    log.debug("钱包 {}: 交易 {} 的日志在第 {} 行匹配程序 {} (\'{}\') 并在第 {} 行匹配关键词 \'{}\' (\'{}\').", 
                                        formattedWalletAddress, formattedSignature, targetProgramId, i, currentLogEntry, keyword, i+1, nextLogEntry);
                                    return true;
                                }
                            }
                        }
                    } else {
                        // Program ID found on the last line, but keywords were expected.
                        // Since keywords are on the *next* line, this cannot be a match if keywords are required.
                        log.trace("钱包 {}: 交易 {} 的日志在最后一行匹配程序 {}，但期望的指令关键词在下一行。日志: \'{}\'", formattedWalletAddress, formattedSignature, targetProgramId, currentLogEntry);
                    }
                }
            }
        }
        log.trace("钱包 {}: 在交易 {} 的日志中未找到相关的程序ID和后续指令关键词组合。", formattedWalletAddress, formattedSignature);
        return false;
    }
} 