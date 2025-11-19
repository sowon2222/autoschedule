package com.example.sbb.config;

import java.net.URI;
import java.util.List;
import java.util.Objects;

import org.springframework.http.HttpHeaders;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import jakarta.servlet.http.HttpServletRequest;

/**
 * WebSocket Handshake 단계에서 Origin과 TLS를 검증하는 인터셉터.
 * 허용되지 않은 도메인이나 비보안 연결을 조기에 차단한다.
 */
@Component
public class StrictHandshakeInterceptor implements HandshakeInterceptor {

    private final WebSocketSecurityProperties properties;

    public StrictHandshakeInterceptor(WebSocketSecurityProperties properties) {
        this.properties = properties;
    }

    @Override
    public boolean beforeHandshake(@NonNull ServerHttpRequest request,
                                   @NonNull ServerHttpResponse response,
                                   @NonNull WebSocketHandler wsHandler,
                                   @NonNull java.util.Map<String, Object> attributes) {
        String path = request.getURI().getPath();
        
        // SockJS 정적 리소스 요청(info, iframe.html 등)은 허용
        if (path != null && (path.contains("/info") || path.contains("/iframe.html") || path.contains("/jsonp"))) {
            return true;
        }
        
        // 실제 WebSocket / XHR / EventSource 요청에 대해서만 Origin 검사
        // 1) Origin 헤더가 허용된 도메인인지 확인
        if (!isOriginAllowed(request.getHeaders())) {
            return false;
        }
        
        // 2) 배포 환경에서 TLS가 강제된다면 https/wss 연결인지 검사
        if (properties.isRequireSecureHandshake() && !isSecure(request)) {
            return false;
        }
        
        return true;
    }

    @Override
    public void afterHandshake(@NonNull ServerHttpRequest request,
                               @NonNull ServerHttpResponse response,
                               @NonNull WebSocketHandler wsHandler,
                               Exception exception) {
        // no-op
    }

    /**
     * Origin 헤더가 비어 있거나 허용 목록에 포함되어 있는지 점검한다.
     */
    private boolean isOriginAllowed(HttpHeaders headers) {
        List<String> origins = headers.get(HttpHeaders.ORIGIN);
        List<String> allowedOrigins = properties.getAllowedOrigins();
        
        if (origins == null || origins.isEmpty()) {
            // Origin 헤더가 없는 경우(같은 오리진)는 허용
            return true;
        }
        
        return origins.stream()
                .anyMatch(origin -> allowedOrigins.contains(origin));
    }

    /**
     * 요청이 https/wss인지 확인한다. 역프록시 환경을 고려해 X-Forwarded-Proto도 검사한다.
     */
    private boolean isSecure(ServerHttpRequest request) {
        if (request instanceof ServletServerHttpRequest servletRequest) {
            HttpServletRequest httpServletRequest = servletRequest.getServletRequest();
            if (httpServletRequest.isSecure()) {
                return true;
            }
            String forwardedProto = httpServletRequest.getHeader("X-Forwarded-Proto");
            if (forwardedProto != null) {
                return "https".equalsIgnoreCase(forwardedProto) || "wss".equalsIgnoreCase(forwardedProto);
            }
        }

        URI uri = request.getURI();
        return Objects.equals(uri.getScheme(), "https") || Objects.equals(uri.getScheme(), "wss");
    }
}



