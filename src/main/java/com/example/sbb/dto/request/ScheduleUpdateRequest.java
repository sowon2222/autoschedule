package com.example.sbb.dto.request;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
public class ScheduleUpdateRequest {
    private LocalDate rangeStart;
    private LocalDate rangeEnd;
    private Integer score;  // nullable
    // teamId, createdBy는 변경 불가
}

