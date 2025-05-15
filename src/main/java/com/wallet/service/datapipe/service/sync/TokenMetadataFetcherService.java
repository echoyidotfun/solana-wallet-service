package com.wallet.service.datapipe.service.sync;

import com.wallet.service.datapipe.dto.TokenInfoDto;
import com.wallet.service.datapipe.model.Token;
import com.wallet.service.datapipe.repository.TokenRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@Slf4j
@RequiredArgsConstructor
public class TokenMetadataFetcherService {

    private final SolanaTrackerService solanaTrackerService;
    private final TokenRepository tokenRepository;
    private final DataSyncService dataSyncService;

    /**
     * Fetches token metadata and related data from SolanaTracker for a given mint address,
     * and saves it using DataSyncService.
     * This method attempts a single fetch and save operation.
     *
     * @param mintAddress The mint address of the token.
     * @return An Optional containing the Token entity if metadata was found and processed by DataSyncService, otherwise empty.
     *         Note: The actual saving is done by DataSyncService, this method signals if the process was initiated.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW) // To ensure DataSyncService's transactionality is respected or new one is started
    public Optional<Token> fetchAndSaveFullTokenData(String mintAddress) {
        if (mintAddress == null || mintAddress.isEmpty()) {
            log.warn("Mint地址为空，无法获取元数据。");
            return Optional.empty();
        }


        log.info("开始从SolanaTracker获取代币 {} 的完整数据", mintAddress);
        TokenInfoDto tokenInfo = solanaTrackerService.getTokenInfo(mintAddress);

        if (tokenInfo == null || tokenInfo.getToken() == null) {
            log.warn("从SolanaTracker获取代币 {} 的元数据失败或未返回数据。", mintAddress);
            return Optional.empty();
        }

        try {
            dataSyncService.saveTokenData(mintAddress, tokenInfo);
            log.info("已调用DataSyncService处理代币 {} 的数据保存。", mintAddress);
            return tokenRepository.findByMintAddress(mintAddress);
        } catch (Exception e) {
            log.error("调用DataSyncService保存代币 {} 数据时发生错误: {}", mintAddress, e.getMessage(), e);
            return Optional.empty();
        }
    }
} 