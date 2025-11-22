package com.example.sbb.controller.support;

import java.util.Optional;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

public final class AuthenticatedUserResolver {

    /**
     * 사용자가 인증되어 있는지 확인하고, 인증된 사용자 ID를 반환
     */
    private AuthenticatedUserResolver() {
    }

    public static Long requireUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getPrincipal() == null) {
            throw new AccessDeniedException("인증된 사용자만 락을 사용할 수 있습니다.");
        }
        Object principal = authentication.getPrincipal();
        if (principal instanceof Long userId) {
            return userId;
        }
        throw new AccessDeniedException("지원하지 않는 인증 정보입니다.");
    }

    /**
     * 인증된 사용자 ID를 Optional로 반환 (인증되지 않은 경우 빈 Optional)
     */
    public static Optional<Long> getUserId() {
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication == null || authentication.getPrincipal() == null) {
                return Optional.empty();
            }
            Object principal = authentication.getPrincipal();
            if (principal instanceof Long userId) {
                return Optional.of(userId);
            }
            return Optional.empty();
        } catch (Exception e) {
            return Optional.empty();
        }
    }
}

