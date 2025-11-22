package com.example.sbb.dto;

import com.example.sbb.domain.CalendarEvent;
import com.example.sbb.domain.Task;
import com.example.sbb.domain.WorkHour;
import java.time.LocalDate;
import java.util.List;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class SchedulingInput {
    private Long teamId;
    private LocalDate rangeStart;
    private LocalDate rangeEnd;
    
    // 스케줄링에 필요한 모든 입력 데이터
    private List<Task> tasks;                    // 배치할 작업들
    private List<WorkHour> workHours;            // 팀의 근무시간 설정
    private List<CalendarEvent> calendarEvents;  // 고정/반복 이벤트들
}

