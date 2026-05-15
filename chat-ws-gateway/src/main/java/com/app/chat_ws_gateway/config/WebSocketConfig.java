package com.app.chat_ws_gateway.config;

import com.app.chat_ws_gateway.filter.WsHandshakeAuthInterceptor;
import com.app.chat_ws_gateway.handler.ChatWebSocketHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketConfigurer {

    private final ChatWebSocketHandler chatWebSocketHandler;
    private final WsHandshakeAuthInterceptor handshakeAuthInterceptor;
    private final ChatWebSocketHandler notiWebSocketHandler;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(chatWebSocketHandler, "/ws/chat")
                .addHandler(notiWebSocketHandler, "/ws/noti/*")
                .addInterceptors(handshakeAuthInterceptor)
                .setAllowedOriginPatterns("*");
    }
}
