package com.example.sbb.dto.request;

import com.example.sbb.domain.AssignmentSource;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;

@Getter
@Setter
public class AssignmentCreateRequest {
    private Long scheduleId;
    private Long taskId;  // nullable
    private String title;
    private OffsetDateTime startsAt;
    private OffsetDateTime endsAt;
    private AssignmentSource source;
    private Integer slotIndex;  // nullable
    private String meta;  // JSONB as String, nullable
}

