package com.example.sbb.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketTransportRegistration;

import org.springframework.lang.NonNull;

/**
 * STOMP 엔드포인트와 브로커 설정을 담당하고,
 * Handshake/JWT/RateLimit 인터셉터를 전체 WebSocket 파이프라인에 적용한다.
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final StompJwtChannelInterceptor jwtChannelInterceptor;
    private final StompRateLimitingChannelInterceptor rateLimitingChannelInterceptor;
    private final StrictHandshakeInterceptor strictHandshakeInterceptor;
    private final WebSocketSecurityProperties webSocketSecurityProperties;

    public WebSocketConfig(StompJwtChannelInterceptor jwtChannelInterceptor,
                           StompRateLimitingChannelInterceptor rateLimitingChannelInterceptor,
                           StrictHandshakeInterceptor strictHandshakeInterceptor,
                           WebSocketSecurityProperties webSocketSecurityProperties) {
        this.jwtChannelInterceptor = jwtChannelInterceptor;
        this.rateLimitingChannelInterceptor = rateLimitingChannelInterceptor;
        this.strictHandshakeInterceptor = strictHandshakeInterceptor;
        this.webSocketSecurityProperties = webSocketSecurityProperties;
    }

    @Override
    public void configureMessageBroker(@NonNull MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic", "/queue");
        registry.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void registerStompEndpoints(@NonNull StompEndpointRegistry registry) {
        String[] allowedOrigins = webSocketSecurityProperties.getAllowedOrigins()
                .toArray(new String[0]);

        registry.addEndpoint("/ws")
                // Handshake 인터셉터로 Origin/TLS 검증
                .addInterceptors(strictHandshakeInterceptor)
                // application.properties에서 관리하는 Origin 목록 적용
                .setAllowedOrigins(allowedOrigins)
                .withSockJS();
    }

    @Override
    public void configureClientInboundChannel(@NonNull ChannelRegistration registration) {
        // 클라이언트에서 들어오는 STOMP 프레임에 JWT와 레이트 리밋 순으로 적용
        registration.interceptors(rateLimitingChannelInterceptor, jwtChannelInterceptor);
    }

    @Override
    public void configureWebSocketTransport(@NonNull WebSocketTransportRegistration registration) {
        // 메시지 크기 제한도 외부 설정으로 제어한다.
        registration.setMessageSizeLimit(webSocketSecurityProperties.getMessageSizeLimitBytes());
    }
}

