package com.example.sbb.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;

@Getter
@Setter
public class CalendarEventCreateRequest {
    @NotNull(message = "팀 ID는 필수입니다")
    private Long teamId;
    
    private Long ownerId;  // nullable
    
    @NotBlank(message = "제목은 필수입니다")
    private String title;
    
    @NotNull(message = "시작 시간은 필수입니다")
    private OffsetDateTime startsAt;
    
    @NotNull(message = "종료 시간은 필수입니다")
    private OffsetDateTime endsAt;
    
    private Boolean fixed;  // 기본값 false
    private String location;  // nullable
    private String attendees;  // "3,5,9" 형태, nullable
    private String notes;  // nullable
}

