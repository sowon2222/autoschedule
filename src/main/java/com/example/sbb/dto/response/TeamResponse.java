package com.example.sbb.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;

@Getter
@Setter
@Schema(description = "팀 응답 DTO")
public class TeamResponse {
    @Schema(description = "팀 ID", example = "1")
    private Long id;
    @Schema(description = "팀 이름", example = "플랫폼팀")
    private String name;
    @Schema(description = "팀 생성 시각", example = "2025-11-01T09:00:00+09:00")
    private OffsetDateTime createdAt;
}

