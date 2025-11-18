package com.example.sbb.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;
import io.swagger.v3.oas.annotations.media.Schema;

@Getter
@Setter
@Schema(description = "캘린더 이벤트 생성 요청 DTO")
public class CalendarEventCreateRequest {
    @Schema(description = "팀 ID", example = "1")
    @NotNull(message = "팀 ID는 필수입니다")
    private Long teamId;
    
    @Schema(description = "이벤트 소유자 ID (선택)", example = "2")
    private Long ownerId;  // nullable
    
    @Schema(description = "이벤트 제목", example = "스프린트 회의")
    @NotBlank(message = "제목은 필수입니다")
    private String title;
    
    @Schema(description = "시작 시각", example = "2025-12-02T10:00:00+09:00")
    @NotNull(message = "시작 시간은 필수입니다")
    private OffsetDateTime startsAt;
    
    @Schema(description = "종료 시각", example = "2025-12-02T11:00:00+09:00")
    @NotNull(message = "종료 시간은 필수입니다")
    private OffsetDateTime endsAt;
    
    @Schema(description = "고정 여부", example = "false")
    private Boolean fixed;  // 기본값 false
    @Schema(description = "이벤트 장소", example = "회의실 A")
    private String location;  // nullable
    @Schema(description = "참석자 ID 목록(쉼표 구분)", example = "3,5,7")
    private String attendees;  // "3,5,9" 형태, nullable
    @Schema(description = "이벤트 메모", example = "자료 준비 필수")
    private String notes;  // nullable
    
    @Schema(description = "반복 주기", example = "WEEKLY", allowableValues = {"DAILY", "WEEKLY", "MONTHLY", "YEARLY"})
    private String recurrenceType;  // 'DAILY', 'WEEKLY', 'MONTHLY', 'YEARLY', null
    
    @Schema(description = "반복 종료일", example = "2025-12-31T23:59:59+09:00")
    private OffsetDateTime recurrenceEndDate;  // nullable, null이면 1년 후까지
}

