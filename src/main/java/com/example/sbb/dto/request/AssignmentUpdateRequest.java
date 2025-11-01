package com.example.sbb.dto.request;

import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;

@Getter
@Setter
public class AssignmentUpdateRequest {
    private String title;
    private OffsetDateTime startsAt;
    private OffsetDateTime endsAt;
    private Integer slotIndex;  // nullable
    private String meta;  // JSONB as String, nullable
    // scheduleId, taskId, source는 변경 불가 (재생성 필요)
}

