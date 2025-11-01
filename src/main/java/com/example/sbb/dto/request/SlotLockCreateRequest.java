package com.example.sbb.dto.request;

import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;

@Getter
@Setter
public class SlotLockCreateRequest {
    private String slotKey;  // 예: "team42:20251030:1000"
    private Long userId;  // nullable
    private OffsetDateTime expiresAt;
}

