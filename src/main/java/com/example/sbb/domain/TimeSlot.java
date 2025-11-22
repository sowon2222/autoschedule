package com.example.sbb.domain;

import java.time.LocalDate;
import java.time.OffsetDateTime;
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
    
    private LocalDate date;              // 날짜
    private int slotIndex;               // 슬롯 인덱스 (0~47, 하루를 30분 단위로 분할)
    private OffsetDateTime startTime;    // 시작 시각
    private OffsetDateTime endTime;      // 종료 시각
    private boolean available;           // 사용 가능 여부
    private Long userId;                 // 소유자 (근무시간을 가진 사용자)
    
    /**
     * 슬롯 인덱스로부터 시작 시각 계산
     * slotIndex 0 = 00:00, slotIndex 1 = 00:30, ..., slotIndex 47 = 23:30
     */
    public static OffsetDateTime calculateStartTime(LocalDate date, int slotIndex) {
        int hours = slotIndex / 2;
        int minutes = (slotIndex % 2) * 30;
        return date.atTime(hours, minutes).atOffset(java.time.ZoneOffset.UTC);
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

