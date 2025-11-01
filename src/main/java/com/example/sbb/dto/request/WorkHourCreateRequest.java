package com.example.sbb.dto.request;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class WorkHourCreateRequest {
    private Long teamId;
    private Long userId;  // nullable (팀 기본 근무시간)
    private Integer dow;  // 1=월, ..., 7=일
    private Integer startMin;  // 분 단위 (예: 540 = 09:00)
    private Integer endMin;  // 분 단위 (예: 1080 = 18:00)
}

