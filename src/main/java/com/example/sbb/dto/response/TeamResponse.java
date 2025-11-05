package com.example.sbb.dto.response;

import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;

/**
 * 팀 정보 응답 DTO
 */
@Getter
@Setter
public class TeamResponse {
    private Long id;
    private String name;
    private OffsetDateTime createdAt;
}

