package com.example.sbb.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "미팅 시간 추천 응답 DTO")
public class MeetingSuggestionResponse {
    
    @Schema(description = "추천된 미팅 시간 목록 (시간순 정렬)")
    private List<SuggestedTimeSlot> suggestions;
    
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "추천된 시간대")
    public static class SuggestedTimeSlot {
        @Schema(description = "시작 시각", example = "2025-11-24T10:00:00+09:00")
        private OffsetDateTime startsAt;
        
        @Schema(description = "종료 시각", example = "2025-11-24T11:00:00+09:00")
        private OffsetDateTime endsAt;
        
        @Schema(description = "가능한 참석자 수", example = "5")
        private Integer availableParticipants;
        
        @Schema(description = "전체 참석자 수", example = "5")
        private Integer totalParticipants;
        
        @Schema(description = "선호도 점수 (높을수록 좋음)", example = "0.85")
        private Double preferenceScore;
    }
}

