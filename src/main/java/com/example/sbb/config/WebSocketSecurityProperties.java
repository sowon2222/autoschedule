package com.example.sbb.config;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * WebSocket 보안 관련 설정값을 외부에서 주입받기 위한 Properties 클래스.
 * application.properties 의 app.websocket.* 항목을 자동 매핑한다.
 */
@Component
@ConfigurationProperties(prefix = "app.websocket")
public class WebSocketSecurityProperties {

    /**
     * WebSocket/STOMP handshake를 허용할 Origin 목록.
     * 환경 변수 APP_WEBSOCKET_ALLOWED_ORIGINS가 설정되어 있으면 우선 사용.
     */
    @Value("${APP_WEBSOCKET_ALLOWED_ORIGINS:${app.websocket.allowed-origins:}}")
    private String allowedOriginsEnv;

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

    /**
     * 환경 변수에서 설정된 Origin 목록을 우선 사용하고,
     * 없으면 application.properties의 값을 사용합니다.
     */
    public List<String> getAllowedOrigins() {
        // 1순위: 시스템 환경 변수 직접 읽기
        String envValue = System.getenv("APP_WEBSOCKET_ALLOWED_ORIGINS");
        if (StringUtils.hasText(envValue)) {
            return Arrays.stream(envValue.split(","))
                    .map(String::trim)
                    .filter(StringUtils::hasText)
                    .collect(Collectors.toList());
        }
        
        // 2순위: @Value로 주입된 값
        if (StringUtils.hasText(allowedOriginsEnv)) {
            return Arrays.stream(allowedOriginsEnv.split(","))
                    .map(String::trim)
                    .filter(StringUtils::hasText)
                    .collect(Collectors.toList());
        }
        
        // 3순위: application.properties 기본값
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



