package com.example.sbb.dto.response;

import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;

@Getter
@Setter
public class TeamResponse {
    private Long id;
    private String name;
    private OffsetDateTime createdAt;
}

