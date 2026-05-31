package com.tokit.global.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // 클라이언트에서 웹소켓 연결할 엔드포인트 등록
        registry.addEndpoint("/ws-tokit")
                .setAllowedOriginPatterns("*") // CORS 전체 허용 (개발)
                .withSockJS();
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        // 클라이언트에서 메시지 송신 시 prefix
        registry.setApplicationDestinationPrefixes("/app");
        
        // 클라이언트가 메시지를 수신(구독)할 때 prefix
        registry.enableSimpleBroker("/topic");
    }
}
