package com.example.sbb.dto.response;

import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;

@Getter
@Setter
public class SlotLockResponse {
    private String slotKey;
    private Long userId;
    private String userName;  // nullable
    private OffsetDateTime expiresAt;
}

