package com.example.sbb.dto.response;

import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;

@Getter
@Setter
public class TaskResponse {
    private Long id;
    private Long teamId;
    private String teamName;  // 간단한 팀 정보
    private Long assigneeId;
    private String assigneeName;  // 간단한 담당자 정보
    private String title;
    private Integer durationMin;
    private OffsetDateTime dueAt;
    private Integer priority;
    private Boolean splittable;
    private String tags;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}

