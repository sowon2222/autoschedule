package com.example.sbb.domain;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

/**
 * 30분 단위 시간 슬롯
 * 스케줄링에서 작업을 배치할 수 있는 최소 시간 단위
 */
@Getter
@Setter
@Builder
@AllArgsConstructor
public class TimeSlot {
    
    // 한국 시간대 (UTC+9)
    private static final ZoneOffset KOREA_OFFSET = ZoneOffset.of("+09:00");
    
    private LocalDate date;              // 날짜
    private int slotIndex;               // 슬롯 인덱스 (0~47, 하루를 30분 단위로 분할)
    private OffsetDateTime startTime;    // 시작 시각
    private OffsetDateTime endTime;      // 종료 시각
    private boolean available;           // 사용 가능 여부
    private Long userId;                 // 소유자 (근무시간을 가진 사용자)
    @Builder.Default
    private double preferenceScore = 1.0; // 선호도 점수 (0.0~1.0, 높을수록 선호)
    
    /**
     * 슬롯 인덱스로부터 시작 시각 계산
     * slotIndex 0 = 00:00, slotIndex 1 = 00:30, ..., slotIndex 47 = 23:30
     * 
     * 중요!!!!: 프론트엔드에서 마감일을 한국 시간 기준으로 입력하고 toISOString()으로 UTC 변환하므로,
     * 슬롯도 한국 시간대(UTC+9)로 생성한 후 UTC로 변환하여 일관성 유지
     * 예: 한국 시간 오후 6시 (18:00 KST) = UTC 오전 9시 (09:00 UTC) 
     * -> 더 좋은 방법 생각해보기 
     */
    public static OffsetDateTime calculateStartTime(LocalDate date, int slotIndex) {
        int hours = slotIndex / 2;
        int minutes = (slotIndex % 2) * 30;
        // 한국 시간대(UTC+9)로 생성한 후 UTC로 변환
        // 이렇게 하면 프론트엔드에서 입력한 마감일(UTC 변환됨)과 시간대가 일치함
        return date.atTime(hours, minutes)
            .atOffset(KOREA_OFFSET)  // 한국 시간대로 먼저 생성
            .withOffsetSameInstant(ZoneOffset.UTC);  // UTC로 변환
    }
    
    /**
     * 슬롯 인덱스로부터 종료 시각 계산 (30분 후)
     */
    public static OffsetDateTime calculateEndTime(LocalDate date, int slotIndex) {
        return calculateStartTime(date, slotIndex).plusMinutes(30);
    }
    
    /**
     * 두 슬롯이 연속인지 확인
     */
    public boolean isConsecutive(TimeSlot other) {
        if (!this.date.equals(other.date) || !this.userId.equals(other.userId)) {
            return false;
        }
        return Math.abs(this.slotIndex - other.slotIndex) == 1;
    }
    
    /**
     * 슬롯 키 생성 (고유 식별자)
     */
    public String getSlotKey() {
        return String.format("%d:%s:%d", userId, date, slotIndex);
    }
}

