package com.wallet.service.datapipe.client.quicknode.wss.event;

import org.springframework.context.ApplicationEvent;

public class WebSocketConnectionEvent extends ApplicationEvent {
    private final boolean connected;

    public WebSocketConnectionEvent(Object source, boolean connected) {
        super(source);
        this.connected = connected;
    }

    public boolean isConnected() {
        return connected;
    }
} 