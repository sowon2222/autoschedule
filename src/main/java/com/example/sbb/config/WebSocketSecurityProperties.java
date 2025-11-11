package com.example.sbb.config;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * WebSocket 보안 관련 설정값을 외부에서 주입받기 위한 Properties 클래스.
 * application.properties 의 app.websocket.* 항목을 자동 매핑한다.
 */
@Component
@ConfigurationProperties(prefix = "app.websocket")
public class WebSocketSecurityProperties {

    /**
     * WebSocket/STOMP handshake를 허용할 Origin 목록.
     */
    private List<String> allowedOrigins = new ArrayList<>(Arrays.asList(
            "http://localhost:5173",
            "http://localhost:3000",
            "http://localhost:8080"
    ));

    /**
     * SockJS 메시지 크기 제한 (bytes).
     */
    private int messageSizeLimitBytes = 64 * 1024;

    /**
     * wss(https) 프로토콜만 허용할지 여부. 역프록시 사용 시 X-Forwarded-Proto 헤더를 확인한다.
     */
    private boolean requireSecureHandshake = false;

    private final RateLimitProperties rateLimit = new RateLimitProperties();

    public List<String> getAllowedOrigins() {
        return allowedOrigins;
    }

    public void setAllowedOrigins(List<String> allowedOrigins) {
        this.allowedOrigins = allowedOrigins;
    }

    public int getMessageSizeLimitBytes() {
        return messageSizeLimitBytes;
    }

    public void setMessageSizeLimitBytes(int messageSizeLimitBytes) {
        this.messageSizeLimitBytes = messageSizeLimitBytes;
    }

    public boolean isRequireSecureHandshake() {
        return requireSecureHandshake;
    }

    public void setRequireSecureHandshake(boolean requireSecureHandshake) {
        this.requireSecureHandshake = requireSecureHandshake;
    }

    public RateLimitProperties getRateLimit() {
        return rateLimit;
    }

    /**
     * STOMP 메시지 전송량 제한에 사용되는 속성 묶음.
     */
    public static class RateLimitProperties {
        /**
         * Rate limit 윈도우(초).
         */
        private long windowSeconds = 10L;

        /**
         * Rate limit 윈도우 내 허용 메시지 수.
         */
        private int maxMessages = 60;

        public long getWindowSeconds() {
            return windowSeconds;
        }

        public void setWindowSeconds(long windowSeconds) {
            this.windowSeconds = windowSeconds;
        }

        public int getMaxMessages() {
            return maxMessages;
        }

        public void setMaxMessages(int maxMessages) {
            this.maxMessages = maxMessages;
        }
    }
}



