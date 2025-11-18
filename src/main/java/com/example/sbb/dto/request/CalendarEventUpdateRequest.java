package com.example.sbb.dto.request;

import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;
import io.swagger.v3.oas.annotations.media.Schema;

@Getter
@Setter
@Schema(description = "캘린더 이벤트 수정 요청 DTO")
public class CalendarEventUpdateRequest {
    @Schema(description = "이벤트 제목", example = "스프린트 회의")
    private String title;
    @Schema(description = "시작 시각", example = "2025-12-02T10:00:00+09:00")
    private OffsetDateTime startsAt;
    @Schema(description = "종료 시각", example = "2025-12-02T11:00:00+09:00")
    private OffsetDateTime endsAt;
    @Schema(description = "고정 여부", example = "true")
    private Boolean fixed;
    @Schema(description = "이벤트 장소", example = "회의실 A")
    private String location;  // nullable
    @Schema(description = "참석자 ID 리스트(쉼표 구분)", example = "3,5,7")
    private String attendees;  // "3,5,9" 형태, nullable
    @Schema(description = "이벤트 메모", example = "안건 공유 예정")
    private String notes;  // nullable
    
    @Schema(description = "반복 주기", example = "WEEKLY", allowableValues = {"DAILY", "WEEKLY", "MONTHLY", "YEARLY"})
    private String recurrenceType;  // 'DAILY', 'WEEKLY', 'MONTHLY', 'YEARLY', null
    
    @Schema(description = "반복 종료일", example = "2025-12-31T23:59:59+09:00")
    private OffsetDateTime recurrenceEndDate;  // nullable
    // teamId, ownerId는 변경 불가
}

