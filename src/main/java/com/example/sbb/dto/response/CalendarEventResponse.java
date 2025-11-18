package com.example.sbb.dto.response;

import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;

@Getter
@Setter
public class CalendarEventResponse {
    private Long id;
    private Long teamId;
    private String teamName;  // 간단한 팀 정보
    private Long ownerId;
    private String ownerName;  // 간단한 소유자 정보
    private String title;
    private OffsetDateTime startsAt;
    private OffsetDateTime endsAt;
    private Boolean fixed;
    private String location;
    private String attendees;
    private String notes;
    private String recurrenceType;
    private OffsetDateTime recurrenceEndDate;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}

