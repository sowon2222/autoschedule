package com.example.sbb.dto.response;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ScheduleResponse {
    private Long id;
    private Long teamId;
    private String teamName;
    private LocalDate rangeStart;
    private LocalDate rangeEnd;
    private Integer score;
    private Long createdBy;
    private String createdByName;
    private OffsetDateTime createdAt;
    private List<AssignmentResponse> assignments;  // 배치된 작업 목록
}

