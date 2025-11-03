package com.example.sbb.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "jwt")
public class JwtProperties {
    private String secret = "your-secret-key-should-be-very-long-and-secure-at-least-256-bits-for-hs256-algorithm-change-this-in-production";
    private long expiration = 86400000; // 24시간 (밀리초)
    private long refreshExpiration = 604800000; // 7일 (밀리초)
}

