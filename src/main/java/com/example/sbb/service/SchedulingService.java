package com.example.sbb.service;

import com.example.sbb.domain.Assignment;
import com.example.sbb.domain.CalendarEvent;
import com.example.sbb.domain.Schedule;
import com.example.sbb.domain.Task;
import com.example.sbb.domain.Team;
import com.example.sbb.domain.TimeSlot;
import com.example.sbb.domain.User;
import com.example.sbb.domain.WorkHour;
import com.example.sbb.dto.SchedulingInput;
import com.example.sbb.dto.response.ScheduleGenerateResponse;
import com.example.sbb.dto.response.ScheduleResponse;
import com.example.sbb.repository.AssignmentRepository;
import com.example.sbb.repository.CalendarEventRepository;
import com.example.sbb.repository.ScheduleRepository;
import com.example.sbb.repository.TaskRepository;
import com.example.sbb.repository.TeamMemberRepository;
import com.example.sbb.repository.TeamRepository;
import com.example.sbb.repository.WorkHourRepository;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 스케줄링 서비스
 * 입력 데이터를 수집하고 스케줄을 생성하는 기본 틀
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SchedulingService {

    private final TaskRepository taskRepository;
    private final WorkHourRepository workHourRepository;
    private final CalendarEventRepository calendarEventRepository;
    private final ScheduleRepository scheduleRepository;
    private final AssignmentRepository assignmentRepository;
    private final TeamRepository teamRepository;
    private final TeamMemberRepository teamMemberRepository;
    private final ScheduleOptimizationService scheduleOptimizationService;
    private final ScheduleService scheduleService;
    private final TimeSlotGenerator timeSlotGenerator;
    private final GreedyScheduler greedyScheduler;
    private final ScoreCalculator scoreCalculator;
    private final LocalSearchOptimizer localSearchOptimizer;

    /**
     * 스케줄 생성에 필요한 입력 데이터를 수집합니다.
     * 
     * @param teamId 팀 ID
     * @param rangeStart 스케줄 시작일
     * @param rangeEnd 스케줄 종료일
     * @return 스케줄링 입력 데이터
     */
    @Transactional(readOnly = true)
    public SchedulingInput collectInputData(Long teamId, LocalDate rangeStart, LocalDate rangeEnd) {
        log.info("스케줄링 입력 데이터 수집 시작: teamId={}, range={} ~ {}", teamId, rangeStart, rangeEnd);
        
        // 날짜 범위를 OffsetDateTime으로 변환 (하루의 시작과 끝)
        OffsetDateTime startDateTime = rangeStart.atStartOfDay().atOffset(ZoneOffset.UTC);
        OffsetDateTime endDateTime = rangeEnd.atTime(23, 59, 59).atOffset(ZoneOffset.UTC);
        
        // 1. 작업(Task) 수집 - 마감일이 범위 내에 있거나 null인 작업들
        List<Task> tasks = taskRepository.findByTeamIdAndDueAtBetween(teamId, startDateTime, endDateTime);
        log.info("수집된 작업 수: {}", tasks.size());
        
        // 2. 근무시간(WorkHour) 수집
        List<WorkHour> workHours = workHourRepository.findByTeam_Id(teamId);
        log.info("수집된 근무시간 설정 수: {}", workHours.size());
        
        // 3. 캘린더 이벤트(CalendarEvent) 수집 - 범위 내의 이벤트들
        // TODO: 반복 이벤트 확장 로직 추가 필요 (Day 16에서 구현)
        List<CalendarEvent> calendarEvents = calendarEventRepository.findAll().stream()
            .filter(e -> e.getTeam().getId().equals(teamId))
            .filter(e -> isEventInRange(e, startDateTime, endDateTime))
            .toList();
        log.info("수집된 캘린더 이벤트 수: {}", calendarEvents.size());
        
        return SchedulingInput.builder()
            .teamId(teamId)
            .rangeStart(rangeStart)
            .rangeEnd(rangeEnd)
            .tasks(tasks)
            .workHours(workHours)
            .calendarEvents(calendarEvents)
            .build();
    }

    /**
     * 이벤트가 주어진 날짜 범위와 겹치는지 확인
     * 반복 이벤트는 아직 처리하지 않음 (Day 16에서 구현)
     */
    private boolean isEventInRange(CalendarEvent event, OffsetDateTime start, OffsetDateTime end) {
        // 고정 이벤트인 경우 직접 비교
        if (event.getRecurrenceType() == null) {
            return !event.getEndsAt().isBefore(start) && !event.getStartsAt().isAfter(end);
        }
        
        // 반복 이벤트는 일단 원본 이벤트만 확인 (Day 16에서 확장)
        return !event.getEndsAt().isBefore(start) && !event.getStartsAt().isAfter(end);
    }

    /**
     * 스케줄 생성 메인 메서드 (비동기 실행)
     * 
     * @param teamId 팀 ID
     * @param rangeStart 스케줄 시작일
     * @param rangeEnd 스케줄 종료일
     * @param userId 생성자 사용자 ID (nullable)
     */
    @Async
    @Transactional
    public void generateSchedule(Long teamId, LocalDate rangeStart, LocalDate rangeEnd, Long userId) {
        log.info("스케줄 생성 시작: teamId={}, range={} ~ {}", teamId, rangeStart, rangeEnd);
        
        try {
            // 진행률 브로드캐스트: 데이터 수집 시작
            scheduleOptimizationService.publishProgress(teamId, 10, "입력 데이터 수집 중...");
            
            // 입력 데이터 수집
            SchedulingInput input = collectInputData(teamId, rangeStart, rangeEnd);
            
            // 진행률 브로드캐스트: 데이터 수집 완료
            scheduleOptimizationService.publishProgress(teamId, 20, "입력 데이터 수집 완료");
            
            // Day 16: 슬롯 생성
            scheduleOptimizationService.publishProgress(teamId, 30, "시간 슬롯 생성 중...");
            
            // 팀 멤버 ID 목록 가져오기 (팀 기본 근무시간 적용용)
            List<Long> teamMemberIds = teamMemberRepository.findByTeamId(teamId).stream()
                .map(tm -> tm.getUser().getId())
                .collect(java.util.stream.Collectors.toList());
            
            // 근무시간이 없으면 기본 근무시간 생성
            List<WorkHour> workHours = input.getWorkHours();
            if (workHours.isEmpty() && !teamMemberIds.isEmpty()) {
                log.warn("근무시간 설정이 없어 기본 근무시간(9시-18시, 월-일)을 사용합니다.");
                Team team = teamRepository.findById(teamId)
                    .orElseThrow(() -> new IllegalArgumentException("팀을 찾을 수 없습니다: " + teamId));
                
                // 주말 포함 모든 요일에 기본 근무시간 생성 (주말은 선호도가 낮게 설정됨)
                for (int dow = 1; dow <= 7; dow++) { // 월요일(1) ~ 일요일(7) - DB 형식
                    WorkHour defaultWorkHour = new WorkHour();
                    defaultWorkHour.setTeam(team);
                    defaultWorkHour.setUser(null); // 팀 기본 근무시간
                    defaultWorkHour.setDow(dow); // DB 형식: 1=월요일, 7=일요일
                    defaultWorkHour.setStartMin(540); // 9시
                    defaultWorkHour.setEndMin(1080);  // 18시
                    workHours.add(defaultWorkHour);
                }
                log.info("기본 근무시간 생성 완료: {}개 (월-일, 9시-18시, 주말은 선호도 낮음)", workHours.size());
            }
            
            Map<Long, List<TimeSlot>> availableSlots = timeSlotGenerator.generateAvailableSlots(
                workHours,
                input.getCalendarEvents(),
                rangeStart,
                rangeEnd,
                teamMemberIds
            );
            scheduleOptimizationService.publishProgress(teamId, 40, "시간 슬롯 생성 완료");
            
            // Day 17: 그리디 배치
            scheduleOptimizationService.publishProgress(teamId, 50, "작업 배치 중...");
            
            // Schedule 엔티티 생성
            Team team = teamRepository.findById(teamId)
                .orElseThrow(() -> new IllegalArgumentException("팀을 찾을 수 없습니다: " + teamId));
            
            Schedule schedule = new Schedule();
            schedule.setTeam(team);
            schedule.setRangeStart(rangeStart);
            schedule.setRangeEnd(rangeEnd);
            schedule.setCreatedAt(OffsetDateTime.now(ZoneOffset.UTC));
            
            // createdBy 설정
            if (userId != null) {
                User creator = new User();
                creator.setId(userId);
                schedule.setCreatedBy(creator);
            }
            
            schedule = scheduleRepository.save(schedule);
            
            // 그리디 배치 실행
            List<Assignment> assignments = greedyScheduler.scheduleTasks(
                input.getTasks(),
                availableSlots,
                schedule
            );
            
            // Assignment 저장
            assignmentRepository.saveAll(assignments);
            scheduleOptimizationService.publishProgress(teamId, 70, "작업 배치 완료");
            
            // Day 18: 점수 계산
            scheduleOptimizationService.publishProgress(teamId, 80, "점수 계산 중...");
            int initialScore = scoreCalculator.calculateScore(assignments, input.getTasks(), availableSlots);
            schedule.setScore(initialScore);
            schedule = scheduleRepository.save(schedule);
            scheduleOptimizationService.publishProgress(teamId, 85, String.format("초기 점수: %d점", initialScore));
            
            // Day 19: 로컬서치 개선
            scheduleOptimizationService.publishProgress(teamId, 90, "로컬서치 최적화 중...");
            List<Assignment> optimizedAssignments = localSearchOptimizer.optimize(
                schedule, assignments, input.getTasks(), availableSlots);
            
            // 최적화 후 점수 재계산
            int finalScore = scoreCalculator.calculateScore(optimizedAssignments, input.getTasks(), availableSlots);
            schedule.setScore(finalScore);
            schedule = scheduleRepository.save(schedule);
            scheduleOptimizationService.publishProgress(teamId, 95, 
                String.format("최적화 완료: %d점 (개선: %+d점)", finalScore, finalScore - initialScore));
            
            // 완료 메시지 전송
            ScheduleResponse scheduleResponse = scheduleService.getScheduleById(schedule.getId());
            scheduleOptimizationService.publishCompletion(teamId, scheduleResponse);
            
            log.info("스케줄 생성 완료: teamId={}, scheduleId={}, assignments={}, score={}", 
                teamId, schedule.getId(), optimizedAssignments.size(), finalScore);
            
        } catch (Exception e) {
            log.error("스케줄 생성 실패: teamId={}", teamId, e);
            scheduleOptimizationService.publishFailure(teamId, "스케줄 생성 중 오류 발생: " + e.getMessage());
            throw e; // 트랜잭션 롤백을 위해 예외 재발생
        }
    }

    /**
     * 스케줄 생성 메인 메서드 (동기 실행, FullCalendar 형식 반환)
     * 
     * @param teamId 팀 ID
     * @param rangeStart 스케줄 시작일
     * @param rangeEnd 스케줄 종료일
     * @param userId 생성자 사용자 ID (nullable)
     * @return 스케줄 생성 결과 (FullCalendar 형식)
     */
    @Transactional
    public ScheduleGenerateResponse generateScheduleSync(
            Long teamId, LocalDate rangeStart, LocalDate rangeEnd, Long userId) {
        log.info("스케줄 생성 시작 (동기): teamId={}, range={} ~ {}", teamId, rangeStart, rangeEnd);
        
        // 입력 데이터 수집
        SchedulingInput input = collectInputData(teamId, rangeStart, rangeEnd);
        
        // 팀 멤버 ID 목록 가져오기 (팀 기본 근무시간 적용용)
        List<Long> teamMemberIds = teamMemberRepository.findByTeamId(teamId).stream()
            .map(tm -> tm.getUser().getId())
            .collect(java.util.stream.Collectors.toList());
        
        // 근무시간이 없으면 기본 근무시간 생성
        List<WorkHour> workHours = input.getWorkHours();
        if (workHours.isEmpty() && !teamMemberIds.isEmpty()) {
            log.warn("근무시간 설정이 없어 기본 근무시간(9시-18시, 월-금)을 사용합니다.");
            Team team = teamRepository.findById(teamId)
                .orElseThrow(() -> new IllegalArgumentException("팀을 찾을 수 없습니다: " + teamId));
            
            // 주말 포함 모든 요일에 기본 근무시간 생성 (주말은 선호도가 낮게 설정됨)
            for (int dow = 1; dow <= 7; dow++) { // 월요일(1) ~ 일요일(7) - DB 형식
                WorkHour defaultWorkHour = new WorkHour();
                defaultWorkHour.setTeam(team);
                defaultWorkHour.setUser(null); // 팀 기본 근무시간
                defaultWorkHour.setDow(dow); // DB 형식: 1=월요일, 7=일요일
                defaultWorkHour.setStartMin(540); // 9시
                defaultWorkHour.setEndMin(1080);  // 18시
                workHours.add(defaultWorkHour);
            }
            log.info("기본 근무시간 생성 완료: {}개 (월-일, 9시-18시, 주말은 선호도 낮음)", workHours.size());
        }
        
        // 슬롯 생성
        Map<Long, List<TimeSlot>> availableSlots = timeSlotGenerator.generateAvailableSlots(
            workHours,
            input.getCalendarEvents(),
            rangeStart,
            rangeEnd,
            teamMemberIds
        );
        
        // Schedule 엔티티 생성
        Team team = teamRepository.findById(teamId)
            .orElseThrow(() -> new IllegalArgumentException("팀을 찾을 수 없습니다: " + teamId));
        
        Schedule schedule = new Schedule();
        schedule.setTeam(team);
        schedule.setRangeStart(rangeStart);
        schedule.setRangeEnd(rangeEnd);
        schedule.setCreatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        
        if (userId != null) {
            User creator = new User();
            creator.setId(userId);
            schedule.setCreatedBy(creator);
        }
        
        schedule = scheduleRepository.save(schedule);
        
        // 기존 Assignment 삭제 (중복 방지)
        assignmentRepository.deleteByTeamId(teamId);
        log.info("기존 Assignment 삭제 완료: teamId={}", teamId);
        
        // 그리디 배치 실행
        List<Assignment> assignments = greedyScheduler.scheduleTasks(
            input.getTasks(),
            availableSlots,
            schedule
        );
        
        // Assignment 저장
        assignmentRepository.saveAll(assignments);
        
        // 점수 계산
        int score = scoreCalculator.calculateScore(assignments, input.getTasks(), availableSlots);
        schedule.setScore(score);
        schedule = scheduleRepository.save(schedule);
        
        // 배치되지 않은 작업 찾기
        List<Long> assignedTaskIds = assignments.stream()
            .filter(a -> a.getTask() != null)
            .map(a -> a.getTask().getId())
            .distinct()
            .collect(java.util.stream.Collectors.toList());
        
        List<ScheduleGenerateResponse.UnassignedTask> unassignedTasks = input.getTasks().stream()
            .filter(task -> !assignedTaskIds.contains(task.getId()))
            .map(task -> {
                String reason = "마감일까지 충분한 시간 부족";
                if (task.getDueAt() == null) {
                    reason = "사용 가능한 시간 슬롯 부족";
                } else {
                    // 마감일이 지났는지 확인
                    if (task.getDueAt().isBefore(OffsetDateTime.now())) {
                        reason = "마감일이 이미 지났습니다";
                    }
                }
                return new ScheduleGenerateResponse.UnassignedTask(task.getId(), reason);
            })
            .collect(java.util.stream.Collectors.toList());
        
        // FullCalendar 형식으로 변환
        List<ScheduleGenerateResponse.FullCalendarEvent> scheduleEvents = assignments.stream()
            .filter(a -> a.getTask() != null)
            .map(a -> {
                ScheduleGenerateResponse.FullCalendarEvent event = 
                    new ScheduleGenerateResponse.FullCalendarEvent();
                event.setTaskId(a.getTask().getId());
                event.setTitle(a.getTitle());
                event.setStart(a.getStartsAt().toString());
                event.setEnd(a.getEndsAt().toString());
                // 우선순위에 따라 색상 설정
                int priority = a.getTask().getPriority();
                String color = switch (priority) {
                    case 1 -> "#ef4444"; // 매우 중요 - 빨간색
                    case 2 -> "#f59e0b"; // 중요 - 주황색
                    case 3 -> "#3b82f6"; // 기본 - 파란색
                    case 4 -> "#10b981"; // 낮음 - 초록색
                    case 5 -> "#6b7280"; // 매우 낮음 - 회색
                    default -> "#3b82f6";
                };
                event.setColor(color);
                return event;
            })
            .collect(java.util.stream.Collectors.toList());
        
        // CalendarEvent를 FullCalendar 형식으로 변환
        List<ScheduleGenerateResponse.FullCalendarEvent> calendarEvents = input.getCalendarEvents().stream()
            .map(e -> {
                ScheduleGenerateResponse.FullCalendarEvent event = 
                    new ScheduleGenerateResponse.FullCalendarEvent();
                event.setEventId(e.getId());
                event.setTitle(e.getTitle());
                event.setStart(e.getStartsAt().toString());
                event.setEnd(e.getEndsAt().toString());
                event.setColor("#8b5cf6"); // 캘린더 이벤트는 보라색
                return event;
            })
            .collect(java.util.stream.Collectors.toList());
        
        log.info("스케줄 생성 완료 (동기): teamId={}, scheduleId={}, assignments={}, unassigned={}, score={}", 
            teamId, schedule.getId(), assignments.size(), unassignedTasks.size(), score);
        
        ScheduleGenerateResponse response = new ScheduleGenerateResponse();
        response.setSchedule(scheduleEvents);
        response.setEvents(calendarEvents);
        response.setUnassignedTasks(unassignedTasks);
        response.setScore(score);
        return response;
    }

}

