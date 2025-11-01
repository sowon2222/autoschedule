package com.example.sbb.dto.request;

import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;

@Getter
@Setter
public class TaskCreateRequest {
    private Long teamId;
    private Long assigneeId;  // nullable
    private String title;
    private Integer durationMin;
    private OffsetDateTime dueAt;  // nullable
    private Integer priority;  // 기본값 3
    private Boolean splittable;  // 기본값 true
    private String tags;  // nullable
}

