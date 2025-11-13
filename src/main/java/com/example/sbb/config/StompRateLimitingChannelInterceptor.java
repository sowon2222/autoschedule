package com.example.sbb.config;

import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.stereotype.Component;

/**
 * STOMP SEND 프레임에 대한 전송 빈도 및 페이로드 크기를 제어하는 인터셉터.
 * 사용자 또는 세션이 과도한 트래픽을 발생시키지 못하도록 보호한다.
 */
@Component
public class StompRateLimitingChannelInterceptor implements ChannelInterceptor {

    private final WebSocketSecurityProperties properties;
    private final Map<String, RateWindow> windows = new ConcurrentHashMap<>();

    public StompRateLimitingChannelInterceptor(WebSocketSecurityProperties properties) {
        this.properties = properties;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(message);
        StompCommand command = accessor.getCommand();
        if (command == null) {
            // STOMP 메시지가 아니면 그대로 통과
            return message;
        }

        switch (command) {
            case SEND -> {
                enforceRateLimit(accessor);  // 초과 전송 시 예외
                enforcePayloadLimit(message); // 메시지 크기 제한
            }
            case SUBSCRIBE -> {
                enforceSubscribeRateLimit(accessor); // 구독 빈도 제한
            }
            case DISCONNECT -> cleanup(accessor);
            default -> {
            }
        }

        return message;
    }

    private void enforceRateLimit(StompHeaderAccessor accessor) {
        String key = resolveLimiterKey(accessor);
        if (key == null) {
            throw new MessageDeliveryException("Unauthenticated sessions cannot send STOMP messages");
        }

        long now = Instant.now().toEpochMilli();
        long windowMillis = properties.getRateLimit().getWindowSeconds() * 1000L;

        RateWindow window = windows.compute(key, (k, existing) -> {
            if (existing == null || now - existing.windowStartMs >= windowMillis) {
                // 새 윈도우 시작
                return new RateWindow(now, 1);
            }
            int nextCount = existing.count + 1;
            if (nextCount > properties.getRateLimit().getMaxMessages()) {
                existing.count = nextCount;
                return existing;
            }
            existing.count = nextCount;
            return existing;
        });

        if (window != null
                && now - window.windowStartMs < windowMillis
                && window.count > properties.getRateLimit().getMaxMessages()) {
            // 설정된 제한을 초과하면 메시지를 거부
            throw new MessageDeliveryException("Rate limit exceeded for STOMP session");
        }
    }

    private void enforcePayloadLimit(Message<?> message) {
        Object payload = message.getPayload();
        int limit = properties.getMessageSizeLimitBytes();

        if (payload instanceof byte[] bytes && bytes.length > limit) {
            throw new MessageDeliveryException("STOMP payload exceeds allowed size");
        }
        if (payload instanceof String text && text.getBytes(StandardCharsets.UTF_8).length > limit) {
            throw new MessageDeliveryException("STOMP payload exceeds allowed size");
        }
    }

    /**
     * SUBSCRIBE 프레임에 대한 구독 빈도 제한을 적용합니다.
     * 동일 사용자가 짧은 시간 내에 과도하게 많은 토픽을 구독하는 것을 방지합니다.
     */
    private void enforceSubscribeRateLimit(StompHeaderAccessor accessor) {
        String key = resolveLimiterKey(accessor) + ":subscribe";
        if (key == null) {
            throw new MessageDeliveryException("Unauthenticated sessions cannot subscribe to topics");
        }

        long now = Instant.now().toEpochMilli();
        long windowMillis = properties.getRateLimit().getWindowSeconds() * 1000L;
        // 구독은 SEND보다 더 제한적으로 설정 (예: 10초에 20개)
        int maxSubscribes = Math.max(20, properties.getRateLimit().getMaxMessages() / 3);

        RateWindow window = windows.compute(key, (k, existing) -> {
            if (existing == null || now - existing.windowStartMs >= windowMillis) {
                return new RateWindow(now, 1);
            }
            int nextCount = existing.count + 1;
            if (nextCount > maxSubscribes) {
                existing.count = nextCount;
                return existing;
            }
            existing.count = nextCount;
            return existing;
        });

        if (window != null
                && now - window.windowStartMs < windowMillis
                && window.count > maxSubscribes) {
            throw new MessageDeliveryException("Subscribe rate limit exceeded. Too many subscription attempts.");
        }
    }

    private void cleanup(StompHeaderAccessor accessor) {
        // 세션 종료 시 레이트 윈도우 캐시 정리
        String key = resolveLimiterKey(accessor);
        if (key != null) {
            windows.remove(key);
            windows.remove(key + ":subscribe");
        }
    }

    private String resolveLimiterKey(StompHeaderAccessor accessor) {
        Principal user = accessor.getUser();
        if (user != null) {
            return user.getName();
        }
        return accessor.getSessionId();
    }

    private static final class RateWindow {
        private final long windowStartMs;
        private int count;

        private RateWindow(long windowStartMs, int count) {
            this.windowStartMs = windowStartMs;
            this.count = count;
        }
    }
}

