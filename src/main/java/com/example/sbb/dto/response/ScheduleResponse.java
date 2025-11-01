package com.example.sbb.dto.response;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.OffsetDateTime;

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
}

