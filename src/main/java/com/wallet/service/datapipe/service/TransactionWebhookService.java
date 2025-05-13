package com.wallet.service.datapipe.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wallet.service.datapipe.dto.ParsedTransactionDto;
import com.wallet.service.datapipe.dto.quicknode.QuickNodeTransactionEventDto;
import com.wallet.service.datapipe.model.SmartMoneyTransaction;
import com.wallet.service.datapipe.model.TokenMarketData;
import com.wallet.service.datapipe.repository.TokenMarketDataRepository;
import com.wallet.service.datapipe.model.Trader;
import com.wallet.service.datapipe.repository.SmartMoneyTransactionRepository;
import com.wallet.service.datapipe.repository.TraderRepository;
import com.wallet.service.datapipe.repository.TokenRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * 处理webhook推送的交易数据
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class TransactionWebhookService {

    private static final String SOL_MINT = "So11111111111111111111111111111111111111112";

    private final SmartMoneyTransactionRepository smartMoneyTransactionRepository;
    private final TraderRepository traderRepository;
    private final TransactionAnalysisService transactionAnalysisService;
    private final ObjectMapper objectMapper;
    private final TokenMarketDataRepository tokenMarketDataRepository;
    private final TokenRepository tokenRepository;
    private final TokenMetadataFetcherService tokenMetadataFetcherService;

    /**
     * 处理QuickNode stream接收到的交易事件列表 (JSON数组)
     */
    public void processQuickNodeEvents(String eventsJson) {
        try {
            QuickNodeTransactionEventDto event = objectMapper.readValue(eventsJson, QuickNodeTransactionEventDto.class);
            try {
                processSingleEvent(event);
            } catch (Exception e) {
                // 尝试获取 signature 用于日志记录
                String signature = "N/A";
                if (event.getMatchedTransactions() != null && !event.getMatchedTransactions().isEmpty()) {
                    signature = event.getMatchedTransactions().get(0).getSignature(); 
                }
                log.error("处理交易事件时发生错误: signature={} - {}", signature, e.getMessage(), e);
            }
        } catch (JsonProcessingException e) {
            log.error("解析交易事件JSON数组时发生错误: {}", e.getMessage(), e);
        } catch (Exception e) {
            log.error("处理交易事件列表时发生未知错误: {}", e.getMessage(), e);
        }
    }

    /**
     * 处理单个交易事件 (包含多个matchedTransaction)
     */
    @Transactional // 保证一个event内的所有交易要么都成功，要么都失败
    private void processSingleEvent(QuickNodeTransactionEventDto event) {
        // 调用新的分析服务，获取解析后的标准化交易记录列表
        List<ParsedTransactionDto> parsedTransactions = transactionAnalysisService.analyzeEvent(event);

        for (ParsedTransactionDto parsedTx : parsedTransactions) {
            // 检查交易是否来自被跟踪的"聪明钱"地址
            String walletAddress = parsedTx.getSignerAddress();
            // if (walletAddress == null || !isFromTrackedWallet(walletAddress)) {
            //     log.debug("交易 {} 非来自跟踪钱包 {} 或地址为空，已跳过", parsedTx.getSignature(), walletAddress);
            //     continue;
            // }

            // 检查是否是有效交易 (至少有输入或输出)
            if (parsedTx.getTokenInputMint() == null && parsedTx.getTokenOutputMint() == null) {
                 log.info("交易 {} 没有有效的输入或输出Token，已跳过", parsedTx.getSignature());
                 continue;
            }

            // --- 同步获取缺失的代币元数据 ---
            String inputMint = parsedTx.getTokenInputMint();
            if (inputMint != null && !SOL_MINT.equals(inputMint)) {
                if (!tokenRepository.existsByMintAddress(inputMint)) {
                    log.info("输入代币 {} 的元数据不存在，尝试同步获取。", inputMint);
                    try {
                        tokenMetadataFetcherService.fetchAndSaveFullTokenData(inputMint);
                    } catch (Exception e) {
                        log.error("同步获取输入代币 {} 元数据失败: {}", inputMint, e.getMessage(), e);
                        // 继续处理，价格可能缺失
                    }
                }
            }

            String outputMint = parsedTx.getTokenOutputMint();
            if (outputMint != null && !SOL_MINT.equals(outputMint)) {
                if (!tokenRepository.existsByMintAddress(outputMint)) {
                    log.info("输出代币 {} 的元数据不存在，尝试同步获取。", outputMint);
                    try {
                        tokenMetadataFetcherService.fetchAndSaveFullTokenData(outputMint);
                    } catch (Exception e) {
                        log.error("同步获取输出代币 {} 元数据失败: {}", outputMint, e.getMessage(), e);
                         // 继续处理，价格可能缺失
                    }
                }
            }
            // --- 元数据同步结束 ---

            // 创建交易记录并设置基础信息
            SmartMoneyTransaction txRecord = new SmartMoneyTransaction();
            txRecord.setSignature(parsedTx.getSignature());
            txRecord.setWalletAddress(walletAddress);
            txRecord.setTransactionTime(Timestamp.from(Instant.ofEpochSecond(parsedTx.getTimestamp())));
            txRecord.setSlot(parsedTx.getSlot());
            txRecord.setPlatform(parsedTx.getPlatform());
            txRecord.setProgramId(parsedTx.getProgramId());
            txRecord.setTokenInputMint(inputMint);
            if (parsedTx.getTokenInputAmount() != null) {
                txRecord.setTokenInputAmount(BigDecimal.valueOf(parsedTx.getTokenInputAmount()));
            }
            txRecord.setTokenOutputMint(outputMint);
            if (parsedTx.getTokenOutputAmount() != null) {
                txRecord.setTokenOutputAmount(BigDecimal.valueOf(parsedTx.getTokenOutputAmount()));
            }

            // --- 价格和价值计算 (简化版, 使用修正后的getter) ---
            BigDecimal inputPriceSol = null;
            BigDecimal inputPriceUsd = null;
            BigDecimal outputPriceSol = null;
            BigDecimal outputPriceUsd = null;

            // 查询输入Token价格
            if (txRecord.getTokenInputMint() != null) {
                Optional<TokenMarketData> inputMarketDataOpt = tokenMarketDataRepository.findByMintAddress(txRecord.getTokenInputMint());
                if (inputMarketDataOpt.isPresent()) {
                    TokenMarketData inputMarketData = inputMarketDataOpt.get();
                    // 获取USD价格
                    inputPriceUsd = inputMarketData.getPriceUsd(); 
                    txRecord.setTokenInputPriceInUsd(inputPriceUsd);
                    // 获取SOL价格 (如果报价Token是SOL)
                    if (SOL_MINT.equals(inputMarketData.getQuoteToken())) {
                        inputPriceSol = inputMarketData.getPriceQuote();
                        txRecord.setTokenInputPriceInSol(inputPriceSol);
                    } else {
                        log.debug("输入Token {} 的报价货币非SOL ({}), 无法直接获取SOL价格", txRecord.getTokenInputMint(), inputMarketData.getQuoteToken());
                    }
                } else {
                    log.warn("未找到输入Token的市场数据: {}", txRecord.getTokenInputMint());
                }
            }

            // 查询输出Token价格
            if (txRecord.getTokenOutputMint() != null) {
                Optional<TokenMarketData> outputMarketDataOpt = tokenMarketDataRepository.findByMintAddress(txRecord.getTokenOutputMint());
                if (outputMarketDataOpt.isPresent()) {
                    TokenMarketData outputMarketData = outputMarketDataOpt.get();
                    // 获取USD价格
                    outputPriceUsd = outputMarketData.getPriceUsd();
                    txRecord.setTokenOutputPriceInUsd(outputPriceUsd);
                    // 获取SOL价格 (如果报价Token是SOL)
                    if (SOL_MINT.equals(outputMarketData.getQuoteToken())) {
                        outputPriceSol = outputMarketData.getPriceQuote();
                        txRecord.setTokenOutputPriceInSol(outputPriceSol);
                    } else {
                         log.debug("输出Token {} 的报价货币非SOL ({}), 无法直接获取SOL价格", txRecord.getTokenOutputMint(), outputMarketData.getQuoteToken());
                    }
                } else {
                     log.warn("未找到输出Token的市场数据: {}", txRecord.getTokenOutputMint());
                }
            }

            // 计算总价值 (优先使用输出Token计算)
            BigDecimal totalValueSol = null;
            BigDecimal totalValueUsd = null;

            // 计算SOL总价值
            if (txRecord.getTokenOutputAmount() != null && outputPriceSol != null && outputPriceSol.compareTo(BigDecimal.ZERO) > 0) {
                 totalValueSol = txRecord.getTokenOutputAmount().multiply(outputPriceSol);
            } else if (txRecord.getTokenInputAmount() != null && inputPriceSol != null && inputPriceSol.compareTo(BigDecimal.ZERO) > 0) {
                 // 如果输出价值无法计算，尝试用输入价值
                 totalValueSol = txRecord.getTokenInputAmount().multiply(inputPriceSol);
            }

            // 计算USD总价值
            if (txRecord.getTokenOutputAmount() != null && outputPriceUsd != null && outputPriceUsd.compareTo(BigDecimal.ZERO) > 0) {
                 totalValueUsd = txRecord.getTokenOutputAmount().multiply(outputPriceUsd);
            } else if (txRecord.getTokenInputAmount() != null && inputPriceUsd != null && inputPriceUsd.compareTo(BigDecimal.ZERO) > 0) {
                 // 如果输出价值无法计算，尝试用输入价值
                 totalValueUsd = txRecord.getTokenInputAmount().multiply(inputPriceUsd);
            }

            txRecord.setTotalValueInSol(totalValueSol);
            txRecord.setTotalValueInUsd(totalValueUsd);

            // 保存交易记录
            try {
                smartMoneyTransactionRepository.save(txRecord);
                log.info("已保存聪明钱交易: signature={}, in_mint={}, out_mint={}, value_usd={}, value_sol={}", 
                        txRecord.getSignature(), txRecord.getTokenInputMint(), txRecord.getTokenOutputMint(), totalValueUsd, totalValueSol);
            } catch (Exception e) {
                 log.error("保存聪明钱交易失败: signature={}, error={}", txRecord.getSignature(), e.getMessage(), e);
                 // 可以选择抛出异常让 @Transactional 回滚，或者记录错误继续处理下一个
            }
        }
    }

    /**
     * 获取特定钱包的交易历史
     */
    public List<SmartMoneyTransaction> getWalletTransactions(String walletAddress) {
        return smartMoneyTransactionRepository
                .findByWalletAddressOrderByTransactionTimeDesc(walletAddress);
    }
    
    /**
     * 获取所有聪明钱钱包列表
     */
    public List<Trader> getAllTrackedWallets() {
        return traderRepository.findAll();
    }

} 