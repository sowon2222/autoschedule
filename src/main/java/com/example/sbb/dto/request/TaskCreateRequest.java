package com.example.sbb.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;
import io.swagger.v3.oas.annotations.media.Schema;

@Getter
@Setter
@Schema(description = "작업 생성 요청 DTO")
public class TaskCreateRequest {
    @Schema(description = "팀 ID", example = "1")
    @NotNull(message = "팀 ID는 필수입니다")
    private Long teamId;
    
    @Schema(description = "담당자 ID (선택)", example = "2")
    private Long assigneeId;  // nullable
    
    @Schema(description = "작업 제목", example = "API 문서화 작업")
    @NotBlank(message = "제목은 필수입니다")
    private String title;
    
    @Schema(description = "예상 소요 시간(분)", example = "60")
    @NotNull(message = "소요 시간은 필수입니다")
    @Positive(message = "소요 시간은 양수여야 합니다")
    private Integer durationMin;
    
    @Schema(description = "마감 시각", example = "2025-11-30T12:00:00+09:00")
    private OffsetDateTime dueAt;  // nullable
    
    @Schema(description = "우선순위 (1:높음 ~ 5:낮음)", example = "3")
    @Min(value = 1, message = "우선순위는 1 이상이어야 합니다")
    @Max(value = 5, message = "우선순위는 5 이하여야 합니다")
    private Integer priority;  // 기본값 3
    
    @Schema(description = "분할 가능 여부", example = "true")
    private Boolean splittable;  // 기본값 true
    
    @Schema(description = "태그 목록", example = "backend,api")
    private String tags;  // nullable
}

