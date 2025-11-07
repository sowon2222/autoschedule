package com.example.sbb.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;

@Getter
@Setter
public class TaskUpdateRequest {
    private Long assigneeId;  // nullable
    private String title;
    
    @Positive(message = "소요 시간은 양수여야 합니다")
    private Integer durationMin;
    
    private OffsetDateTime dueAt;  // nullable
    
    @Min(value = 1, message = "우선순위는 1 이상이어야 합니다")
    @Max(value = 5, message = "우선순위는 5 이하여야 합니다")
    private Integer priority;
    
    private Boolean splittable;
    private String tags;  // nullable
    // teamId는 변경 불가
}

