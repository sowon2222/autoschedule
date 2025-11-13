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
import com.example.sbb.repository.TeamMemberRepository;
import com.example.sbb.domain.TeamMemberId;

/**
 * STOMP CONNECT/SEND/SUBSCRIBE 프레임마다 JWT를 검증하고 토픽 접근 권한을 확인하는 인터셉터.
 * WebSocket 연결 이후에도 각 메시지 단위로 인증 정보와 토픽 권한을 확인한다.
 */
@Component
public class StompJwtChannelInterceptor implements ChannelInterceptor {

    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtUtil jwtUtil;
    private final TeamMemberRepository teamMemberRepository;

    /**
     * SockJS CONNECT 프레임에서 토큰을 전파하지 못하는 경우를 대비하여
     * handshake 단계에서 저장한 토큰을 sessionId 기준으로 보관한다.
     */
    private final Map<String, String> sessionTokenCache = new ConcurrentHashMap<>();

    public StompJwtChannelInterceptor(JwtUtil jwtUtil, TeamMemberRepository teamMemberRepository) {
        this.jwtUtil = jwtUtil;
        this.teamMemberRepository = teamMemberRepository;
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
            case SEND -> ensureAuthenticated(accessor);            // 프레임 전송 시 인증 여부 확인
            case SUBSCRIBE -> {
                ensureAuthenticated(accessor);                      // 인증 확인
                validateTopicAccess(accessor);                      // 토픽 접근 권한 검증
            }
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

    /**
     * SUBSCRIBE 프레임의 토픽 접근 권한을 검증합니다.
     * - 팀 관련 토픽: 사용자가 해당 팀의 멤버인지 확인
     * - 사용자 관련 토픽: 본인의 토픽인지 확인
     * - 허용되지 않은 토픽 패턴은 거부
     */
    private void validateTopicAccess(StompHeaderAccessor accessor) {
        String destination = accessor.getDestination();
        if (destination == null) {
            throw new MessageDeliveryException("SUBSCRIBE destination is required");
        }

        Principal principal = accessor.getUser();
        if (principal == null) {
            throw new MessageDeliveryException("Unauthenticated SUBSCRIBE rejected");
        }

        Long userId = Long.parseLong(principal.getName());

        // 허용된 토픽 패턴 검증
        if (destination.startsWith("/topic/tasks/")) {
            validateTeamTopic(destination, userId, "/topic/tasks/");
        } else if (destination.startsWith("/topic/calendar/")) {
            validateTeamTopic(destination, userId, "/topic/calendar/");
        } else if (destination.startsWith("/topic/conflicts/")) {
            validateTeamTopic(destination, userId, "/topic/conflicts/");
        } else if (destination.startsWith("/topic/notifications/team/")) {
            validateTeamTopic(destination, userId, "/topic/notifications/team/");
        } else if (destination.startsWith("/topic/notifications/user/")) {
            validateUserTopic(destination, userId, "/topic/notifications/user/");
        } else if (destination.startsWith("/topic/schedules/")) {
            validateTeamTopic(destination, userId, "/topic/schedules/");
        } else if (destination.startsWith("/topic/detail/")) {
            // detail 토픽은 일단 허용 (추후 필요시 엔티티 소유권 검증 추가 가능)
            // 패턴만 확인
            if (!destination.matches("/topic/detail/[^/]+/\\d+")) {
                throw new MessageDeliveryException("Invalid topic pattern: " + destination);
            }
        } else if (destination.equals("/topic/tasks") || destination.equals("/topic/calendar") 
                || destination.equals("/topic/conflicts") || destination.equals("/topic/notifications")
                || destination.equals("/topic/schedules")) {
            // 루트 레벨 토픽은 허용하지 않음 (보안상 이유)
            throw new MessageDeliveryException("Root level topic subscription not allowed: " + destination);
        } else {
            // 알 수 없는 토픽 패턴은 거부
            throw new MessageDeliveryException("Unauthorized topic pattern: " + destination);
        }
    }

    /**
     * 팀 관련 토픽의 접근 권한을 검증합니다.
     * 사용자가 해당 팀의 멤버인지 확인합니다.
     */
    private void validateTeamTopic(String destination, Long userId, String prefix) {
        String teamIdStr = destination.substring(prefix.length());
        try {
            Long teamId = Long.parseLong(teamIdStr);
            TeamMemberId memberId = new TeamMemberId(teamId, userId);
            if (!teamMemberRepository.existsById(memberId)) {
                throw new MessageDeliveryException("User is not a member of team: " + teamId);
            }
        } catch (NumberFormatException e) {
            throw new MessageDeliveryException("Invalid team ID in topic: " + destination);
        }
    }

    /**
     * 사용자 관련 토픽의 접근 권한을 검증합니다.
     * 본인의 토픽인지 확인합니다.
     */
    private void validateUserTopic(String destination, Long userId, String prefix) {
        String targetUserIdStr = destination.substring(prefix.length());
        try {
            Long targetUserId = Long.parseLong(targetUserIdStr);
            if (!userId.equals(targetUserId)) {
                throw new MessageDeliveryException("User can only subscribe to their own notification topic");
            }
        } catch (NumberFormatException e) {
            throw new MessageDeliveryException("Invalid user ID in topic: " + destination);
        }
    }
}



