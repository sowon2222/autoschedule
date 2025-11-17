package com.example.sbb.config;

import com.example.sbb.util.JwtUtil;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.ArrayList;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    private final JwtUtil jwtUtil;

    public JwtAuthenticationFilter(JwtUtil jwtUtil) {
        this.jwtUtil = jwtUtil;
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain) throws ServletException, IOException {

        // 인증 API와 WebSocket은 필터 통과
        String path = request.getRequestURI();
        if (path.startsWith("/api/auth") || path.startsWith("/ws")) {
            filterChain.doFilter(request, response);
            return;
        }

        // Authorization 헤더에서 토큰 추출
        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = authHeader.substring(7);

        try {
            // 토큰 검증
            if (jwtUtil.isTokenValid(token)) {
                // 사용자 정보 추출
                Long userId = jwtUtil.getUserIdFromToken(token);

                // SecurityContext에 인증 정보 설정
                UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(
                        userId,  // principal (사용자 ID)
                        null,    // credentials (비밀번호, 필요 없음)
                        new ArrayList<>()  // authorities (권한, 나중에 추가 가능)
                    );
                authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(authentication);
            }
        } catch (Exception e) {
            // 토큰이 유효하지 않으면 그냥 통과 (다음 필터에서 인증 실패 처리)
        }

        filterChain.doFilter(request, response);
    }
}

