package com.wallet.service.datapipe.client.quicknode.http;

import java.util.Optional;

import com.wallet.service.datapipe.dto.rpc.SolanaTransactionResponseDto;


public interface SolanaRpcService {
    Optional<SolanaTransactionResponseDto> getTransactionDetails(String signature);
} 