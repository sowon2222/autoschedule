package com.example.sbb.service;

import com.example.sbb.domain.Assignment;
import com.example.sbb.domain.Schedule;
import com.example.sbb.dto.response.AssignmentResponse;
import com.example.sbb.dto.response.ScheduleResponse;
import com.example.sbb.repository.AssignmentRepository;
import com.example.sbb.repository.ScheduleRepository;
import com.example.sbb.repository.TeamRepository;
import com.example.sbb.repository.UserRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 스케줄 조회 서비스
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ScheduleService {

    private final ScheduleRepository scheduleRepository;
    private final AssignmentRepository assignmentRepository;
    private final TeamRepository teamRepository;
    private final UserRepository userRepository;

    /**
     * 스케줄 ID로 조회
     */
    @Transactional(readOnly = true)
    public ScheduleResponse getScheduleById(Long scheduleId) {
        Schedule schedule = scheduleRepository.findById(scheduleId)
            .orElseThrow(() -> new IllegalArgumentException("스케줄을 찾을 수 없습니다: " + scheduleId));
        
        return toScheduleResponse(schedule);
    }

    /**
     * 팀의 최신 스케줄 조회
     */
    @Transactional(readOnly = true)
    public ScheduleResponse getLatestScheduleByTeamId(Long teamId) {
        // 팀의 모든 스케줄 중 가장 최근 생성된 것
        List<Schedule> schedules = scheduleRepository.findByTeamId(teamId);
        
        if (schedules.isEmpty()) {
            return null;
        }
        
        // 가장 최근 생성된 스케줄
        Schedule latest = schedules.stream()
            .max((s1, s2) -> s1.getCreatedAt().compareTo(s2.getCreatedAt()))
            .orElse(null);
        
        return latest != null ? toScheduleResponse(latest) : null;
    }

    /**
     * 팀의 모든 스케줄 조회
     */
    @Transactional(readOnly = true)
    public List<ScheduleResponse> getSchedulesByTeamId(Long teamId) {
        List<Schedule> schedules = scheduleRepository.findByTeamId(teamId);
        
        return schedules.stream()
            .map(this::toScheduleResponse)
            .collect(Collectors.toList());
    }

    /**
     * 스케줄 삭제
     */
    @Transactional
    public void deleteSchedule(Long scheduleId) {
        Schedule schedule = scheduleRepository.findById(scheduleId)
            .orElseThrow(() -> new IllegalArgumentException("스케줄을 찾을 수 없습니다: " + scheduleId));
        scheduleRepository.delete(schedule);
    }

    /**
     * 팀의 모든 스케줄 삭제
     */
    @Transactional
    public void deleteAllSchedulesByTeamId(Long teamId) {
        List<Schedule> schedules = scheduleRepository.findByTeamId(teamId);
        scheduleRepository.deleteAll(schedules);
    }

    /**
     * Schedule 엔티티를 ScheduleResponse로 변환
     */
    private ScheduleResponse toScheduleResponse(Schedule schedule) {
        ScheduleResponse response = new ScheduleResponse();
        response.setId(schedule.getId());
        response.setTeamId(schedule.getTeam().getId());
        response.setTeamName(schedule.getTeam().getName());
        response.setRangeStart(schedule.getRangeStart());
        response.setRangeEnd(schedule.getRangeEnd());
        response.setScore(schedule.getScore());
        
        if (schedule.getCreatedBy() != null) {
            response.setCreatedBy(schedule.getCreatedBy().getId());
            userRepository.findById(schedule.getCreatedBy().getId())
                .ifPresent(user -> response.setCreatedByName(user.getName()));
        }
        
        response.setCreatedAt(schedule.getCreatedAt());
        
        // Assignment 목록 추가
        List<Assignment> assignments = assignmentRepository.findByScheduleId(schedule.getId());
        List<AssignmentResponse> assignmentResponses = assignments.stream()
            .map(this::toAssignmentResponse)
            .collect(Collectors.toList());
        response.setAssignments(assignmentResponses);
        
        return response;
    }

    /**
     * Assignment 엔티티를 AssignmentResponse로 변환
     */
    private AssignmentResponse toAssignmentResponse(Assignment assignment) {
        AssignmentResponse response = new AssignmentResponse();
        response.setId(assignment.getId());
        response.setScheduleId(assignment.getSchedule().getId());
        if (assignment.getTask() != null) {
            response.setTaskId(assignment.getTask().getId());
            response.setTaskTitle(assignment.getTask().getTitle());
        }
        response.setTitle(assignment.getTitle());
        response.setStartsAt(assignment.getStartsAt());
        response.setEndsAt(assignment.getEndsAt());
        response.setSource(assignment.getSource());
        response.setSlotIndex(assignment.getSlotIndex());
        response.setMeta(assignment.getMeta());
        return response;
    }
}

