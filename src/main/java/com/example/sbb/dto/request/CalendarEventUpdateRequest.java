package com.example.sbb.dto.request;

import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;

@Getter
@Setter
public class CalendarEventUpdateRequest {
    private String title;
    private OffsetDateTime startsAt;
    private OffsetDateTime endsAt;
    private Boolean fixed;
    private String location;  // nullable
    private String attendees;  // "3,5,9" 형태, nullable
    private String notes;  // nullable
    // teamId, ownerId는 변경 불가
}

