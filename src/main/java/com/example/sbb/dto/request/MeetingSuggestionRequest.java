package com.example.sbb.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.util.List;

@Getter
@Setter
@Schema(description = "미팅 시간 추천 요청 DTO")
public class MeetingSuggestionRequest {
    
    @Schema(description = "팀 ID", example = "1", required = true)
    @NotNull(message = "팀 ID는 필수입니다")
    private Long teamId;
    
    @Schema(description = "미팅 소요 시간 (분)", example = "60", required = true)
    @NotNull(message = "소요 시간은 필수입니다")
    @Min(value = 15, message = "최소 15분 이상이어야 합니다")
    private Integer durationMin;
    
    @Schema(description = "참석자 ID 목록 (선택, 없으면 팀 전체)", example = "[1, 2, 3]")
    private List<Long> participantIds;  // nullable, 없으면 팀 전체
    
    @Schema(description = "검색 시작일 (선택, 없으면 오늘부터)", example = "2025-11-24")
    private LocalDate preferredStartDate;  // nullable
    
    @Schema(description = "검색 기간 (일, 기본값 14일)", example = "14")
    private Integer searchDays;  // nullable, 기본값 14일
}

