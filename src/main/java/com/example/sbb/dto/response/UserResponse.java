package com.example.sbb.dto.response;

import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;

@Getter
@Setter
public class UserResponse {
    private Long id;
    private String email;
    private String name;
    private OffsetDateTime createdAt;
}

