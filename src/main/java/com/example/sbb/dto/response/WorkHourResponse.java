package com.example.sbb.dto.response;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class WorkHourResponse {
    private Long id;
    private Long teamId;
    private String teamName;
    private Long userId;
    private String userName;  // nullable
    private Integer dow;
    private Integer startMin;
    private Integer endMin;
}

