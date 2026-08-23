package com.tokit.global.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.StompWebSocketEndpointRegistration;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WebSocketConfigTest {

    private WebSocketConfig webSocketConfig;

    @Mock
    private StompEndpointRegistry stompEndpointRegistry;

    @Mock
    private StompWebSocketEndpointRegistration stompWebSocketEndpointRegistration;

    @Mock
    private MessageBrokerRegistry messageBrokerRegistry;

    @BeforeEach
    void setUp() {
        webSocketConfig = new WebSocketConfig();
    }

    @Test
    @DisplayName("registerStompEndpoints: /ws-tokit 엔드포인트가 등록되고 SockJS 및 CORS 허용 설정이 활성화된다.")
    void registerStompEndpoints_RegistersWsTokitEndpoint() {
        // Given
        when(stompEndpointRegistry.addEndpoint("/ws-tokit")).thenReturn(stompWebSocketEndpointRegistration);
        when(stompWebSocketEndpointRegistration.setAllowedOriginPatterns(anyString())).thenReturn(stompWebSocketEndpointRegistration);

        // When
        webSocketConfig.registerStompEndpoints(stompEndpointRegistry);

        // Then
        verify(stompEndpointRegistry, times(1)).addEndpoint("/ws-tokit");
        verify(stompWebSocketEndpointRegistration, times(1)).setAllowedOriginPatterns("*");
        verify(stompWebSocketEndpointRegistration, times(1)).withSockJS();
    }

    @Test
    @DisplayName("configureMessageBroker: /app 송신 prefix 및 /topic 실시간 구독 브로커가 등록된다.")
    void configureMessageBroker_ConfiguresPrefixes() {
        // When
        webSocketConfig.configureMessageBroker(messageBrokerRegistry);

        // Then
        verify(messageBrokerRegistry, times(1)).setApplicationDestinationPrefixes("/app");
        verify(messageBrokerRegistry, times(1)).enableSimpleBroker("/topic");
    }
}
