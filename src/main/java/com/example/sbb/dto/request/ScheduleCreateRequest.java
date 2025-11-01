package com.example.sbb.dto.request;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
public class ScheduleCreateRequest {
    private Long teamId;
    private LocalDate rangeStart;
    private LocalDate rangeEnd;
    private Integer score;  // nullable
    private Long createdBy;  // nullable
}

