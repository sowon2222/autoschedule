package com.example.sbb.dto.response;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 스케줄 생성 응답
 * FullCalendar에서 바로 사용할 수 있는 형태
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ScheduleGenerateResponse {
    
    /**
     * FullCalendar 이벤트 형식의 스케줄 목록
     */
    private List<FullCalendarEvent> schedule;
    
    /**
     * FullCalendar 이벤트 형식의 캘린더 이벤트 목록
     */
    private List<FullCalendarEvent> events;
    
    /**
     * 배치되지 않은 작업 목록
     */
    private List<UnassignedTask> unassignedTasks;
    
    /**
     * 스케줄 최적화 점수
     */
    private Integer score;
    
    /**
     * FullCalendar 이벤트 형식
     */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FullCalendarEvent {
        private Long taskId;  // Task ID (Assignment인 경우)
        private Long eventId; // CalendarEvent ID (이벤트인 경우)
        private String title;
        private String start;  // ISO 8601 형식: "2025-11-13T09:00"
        private String end;    // ISO 8601 형식: "2025-11-13T10:30"
        private String color; // 색상 코드 (예: "#f59e0b")
    }
    
    /**
     * 배치되지 않은 작업
     */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UnassignedTask {
        private Long taskId;
        private String reason; // 배치 실패 이유
    }
}

