package com.wallet.service.datapipe.dto.quicknode.wss;

import java.util.List;


import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.Builder.Default;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WebSocketRequestDto {
    @Default
    private String jsonrpc = "2.0";
    private long id;
    private String method;
    private List<Object> params;


} 