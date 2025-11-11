package com.example.sbb.config;

import java.security.Principal;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.lang.Nullable;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import com.example.sbb.util.JwtUtil;

/**
 * STOMP CONNECT/SEND/SUBSCRIBE 프레임마다 JWT를 검증하는 인터셉터.
 * WebSocket 연결 이후에도 각 메시지 단위로 인증 정보를 확인한다.
 */
@Component
public class StompJwtChannelInterceptor implements ChannelInterceptor {

    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtUtil jwtUtil;

    /**
     * SockJS CONNECT 프레임에서 토큰을 전파하지 못하는 경우를 대비하여
     * handshake 단계에서 저장한 토큰을 sessionId 기준으로 보관한다.
     */
    private final Map<String, String> sessionTokenCache = new ConcurrentHashMap<>();

    public StompJwtChannelInterceptor(JwtUtil jwtUtil) {
        this.jwtUtil = jwtUtil;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(message);
        StompCommand command = accessor.getCommand();
        if (command == null) {
            // STOMP 프레임이 아니면 그대로 통과
            return message;
        }

        switch (command) {
            case CONNECT -> handleConnect(accessor);               // 최초 연결 시 토큰 검증
            case SEND, SUBSCRIBE -> ensureAuthenticated(accessor); // 프레임 전송/구독 시 인증 여부 확인
            case DISCONNECT -> cleanupSession(accessor);           // 세션 정리
            default -> {
            }
        }

        return message;
    }

    private void handleConnect(StompHeaderAccessor accessor) {
        String sessionId = accessor.getSessionId();
        String rawToken = resolveToken(accessor);

        if (!StringUtils.hasText(rawToken)) {
            // CONNECT 단계에서 토큰이 없으면 연결 자체를 거부
            throw new MessageDeliveryException("Missing Authorization header for STOMP CONNECT");
        }

        String token = normalizeBearerToken(rawToken);
        if (!jwtUtil.isTokenValid(token)) {
            throw new MessageDeliveryException("Invalid JWT token in STOMP CONNECT");
        }

        Long userId = jwtUtil.getUserIdFromToken(token);
        Principal principal = new UsernamePasswordAuthenticationToken(
                userId,
                null,
                Collections.emptyList()
        );

        accessor.setUser(principal);
        accessor.getSessionAttributes().put("userId", userId);
        accessor.getSessionAttributes().put("token", token);

        if (StringUtils.hasText(sessionId)) {
            sessionTokenCache.put(sessionId, token);
        }
    }

    private void ensureAuthenticated(StompHeaderAccessor accessor) {
        // CONNECT에서 인증된 Principal이 없으면 세션 캐시에서 토큰을 복구한다.
        Principal principal = accessor.getUser();
        if (principal == null) {
            String sessionId = accessor.getSessionId();
            String token = sessionId != null ? sessionTokenCache.get(sessionId) : null;
            if (!StringUtils.hasText(token) || !jwtUtil.isTokenValid(token)) {
                throw new MessageDeliveryException("Unauthenticated STOMP frame rejected");
            }
            Long userId = jwtUtil.getUserIdFromToken(token);
            principal = new UsernamePasswordAuthenticationToken(
                    userId,
                    null,
                    Collections.emptyList()
            );
            accessor.setUser(principal);
        }
    }

    private void cleanupSession(StompHeaderAccessor accessor) {
        String sessionId = accessor.getSessionId();
        if (sessionId != null) {
            sessionTokenCache.remove(sessionId);
        }
    }

    @Nullable
    private String resolveToken(StompHeaderAccessor accessor) {
        // STOMP 헤더 우선, 없으면 handshake 단계에서 저장한 세션 속성을 확인
        List<String> nativeHeaders = accessor.getNativeHeader(AUTHORIZATION_HEADER);
        if (!CollectionUtils.isEmpty(nativeHeaders)) {
            return nativeHeaders.get(0);
        }

        Object tokenAttr = accessor.getSessionAttributes() != null
                ? accessor.getSessionAttributes().get("token")
                : null;
        if (tokenAttr instanceof String tokenString) {
            return tokenString;
        }

        return null;
    }

    private String normalizeBearerToken(String value) {
        if (!StringUtils.hasText(value)) {
            return value;
        }
        if (value.startsWith(BEARER_PREFIX)) {
            return value.substring(BEARER_PREFIX.length()).trim();
        }
        return value;
    }
}



