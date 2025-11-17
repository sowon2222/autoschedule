package com.example.sbb.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

import org.springframework.lang.NonNull;

/**
 * 최소 설정으로 WebSocket 연결 테스트용 설정
 * 프로파일: ws-minimal
 * 
 * 이 설정은 StrictHandshakeInterceptor, JWT 인터셉터 등을 모두 제외하고
 * 순수 WebSocket + SockJS만 테스트하기 위한 것입니다.
 */
@Configuration
@EnableWebSocketMessageBroker
@Profile("ws-minimal")
public class MinimalWebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Override
    public void registerStompEndpoints(@NonNull StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns("*")  // 모든 Origin 허용 (테스트용)
                .withSockJS();
    }

    @Override
    public void configureMessageBroker(@NonNull MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic", "/queue");
        registry.setApplicationDestinationPrefixes("/app");
    }
}

