package com.example.sbb.dto.request;

import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;

@Getter
@Setter
public class TaskUpdateRequest {
    private Long assigneeId;  // nullable
    private String title;
    private Integer durationMin;
    private OffsetDateTime dueAt;  // nullable
    private Integer priority;
    private Boolean splittable;
    private String tags;  // nullable
    // teamId는 변경 불가
}

