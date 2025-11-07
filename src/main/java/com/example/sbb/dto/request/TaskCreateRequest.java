package com.example.sbb.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;

@Getter
@Setter
public class TaskCreateRequest {
    @NotNull(message = "팀 ID는 필수입니다")
    private Long teamId;
    
    private Long assigneeId;  // nullable
    
    @NotBlank(message = "제목은 필수입니다")
    private String title;
    
    @NotNull(message = "소요 시간은 필수입니다")
    @Positive(message = "소요 시간은 양수여야 합니다")
    private Integer durationMin;
    
    private OffsetDateTime dueAt;  // nullable
    
    @Min(value = 1, message = "우선순위는 1 이상이어야 합니다")
    @Max(value = 5, message = "우선순위는 5 이하여야 합니다")
    private Integer priority;  // 기본값 3
    
    private Boolean splittable;  // 기본값 true
    
    private String tags;  // nullable
}

