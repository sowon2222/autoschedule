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
        registry.addEndpoint("/ws")
                // Handshake 인터셉터로 Origin/TLS 검증 (실제 Origin 체크는 여기서 수행)
                .addInterceptors(strictHandshakeInterceptor)
                // setAllowedOriginPatterns("*")를 사용하면 SockJS의 originCheck가 비활성화되고
                // allowCredentials와도 충돌하지 않음
                // 실제 Origin 검증은 StrictHandshakeInterceptor에서 수행
                // 이렇게 하면 iframe/jsonp transport도 정상 작동하고, 모든 transport가 동일하게 처리됨
                .setAllowedOriginPatterns("*")
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

