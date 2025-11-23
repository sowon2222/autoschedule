package com.example.sbb.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import io.swagger.v3.oas.annotations.media.Schema;

@Getter
@Setter
@Schema(description = "근무시간 수정 요청 DTO")
public class WorkHourUpdateRequest {
    @Schema(description = "사용자 ID (null이면 팀 기본 근무시간)", example = "2")
    private Long userId;  // nullable (팀 기본 근무시간 변경)
    
    @Schema(description = "요일 (0=월요일, 1=화요일, ..., 6=일요일)", example = "0")
    @NotNull(message = "요일은 필수입니다")
    @Min(value = 0, message = "요일은 0~6 범위여야 합니다")
    @Max(value = 6, message = "요일은 0~6 범위여야 합니다")
    private Integer dow;  // 0=월요일, 1=화요일, ..., 6=일요일
    
    @Schema(description = "시작 시간(분 단위, 예: 540 = 09:00)", example = "540")
    @NotNull(message = "시작 시간은 필수입니다")
    @Min(value = 0, message = "시작 시간은 0 이상이어야 합니다")
    @Max(value = 1440, message = "시작 시간은 1440 이하여야 합니다")
    private Integer startMin;  // 분 단위 (예: 540 = 09:00)
    
    @Schema(description = "종료 시간(분 단위, 예: 1080 = 18:00)", example = "1080")
    @NotNull(message = "종료 시간은 필수입니다")
    @Min(value = 0, message = "종료 시간은 0 이상이어야 합니다")
    @Max(value = 1440, message = "종료 시간은 1440 이하여야 합니다")
    private Integer endMin;  // 분 단위 (예: 1080 = 18:00)
    // teamId는 변경 불가
}

