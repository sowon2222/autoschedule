package com.example.sbb.service;

import com.example.sbb.domain.Assignment;
import com.example.sbb.domain.Schedule;
import com.example.sbb.domain.Task;
import com.example.sbb.dto.response.AssignmentResponse;
import com.example.sbb.repository.AssignmentRepository;
import com.example.sbb.repository.ScheduleRepository;
import com.example.sbb.repository.TaskRepository;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Assignment 서비스
 * Assignment CRUD 기능 제공
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AssignmentService {

    private final AssignmentRepository assignmentRepository;
    private final ScheduleRepository scheduleRepository;
    private final TaskRepository taskRepository;

    /**
     * Assignment 생성
     */
    @Transactional
    public AssignmentResponse createAssignment(Long scheduleId, Long taskId, 
            String title, java.time.OffsetDateTime startsAt, java.time.OffsetDateTime endsAt) {
        Schedule schedule = scheduleRepository.findById(scheduleId)
            .orElseThrow(() -> new IllegalArgumentException("스케줄을 찾을 수 없습니다: " + scheduleId));
        
        Task task = taskId != null ? taskRepository.findById(taskId)
            .orElseThrow(() -> new IllegalArgumentException("작업을 찾을 수 없습니다: " + taskId)) : null;
        
        Assignment assignment = new Assignment();
        assignment.setSchedule(schedule);
        assignment.setTask(task);
        assignment.setTitle(title);
        assignment.setStartsAt(startsAt);
        assignment.setEndsAt(endsAt);
        assignment.setSource(com.example.sbb.domain.AssignmentSource.TASK);
        
        assignment = assignmentRepository.save(assignment);
        log.info("Assignment 생성: id={}, scheduleId={}, taskId={}", 
            assignment.getId(), scheduleId, taskId);
        
        return toAssignmentResponse(assignment);
    }

    /**
     * Assignment 삭제
     */
    @Transactional
    public void deleteAssignment(Long assignmentId) {
        Assignment assignment = assignmentRepository.findById(assignmentId)
            .orElseThrow(() -> new IllegalArgumentException("Assignment를 찾을 수 없습니다: " + assignmentId));
        
        assignmentRepository.delete(assignment);
        log.info("Assignment 삭제: id={}", assignmentId);
    }

    /**
     * 스케줄 ID로 Assignment 목록 조회
     */
    @Transactional(readOnly = true)
    public List<AssignmentResponse> findByScheduleId(Long scheduleId) {
        return assignmentRepository.findByScheduleId(scheduleId);
    }

    /**
     * Task ID로 Assignment 목록 조회
     */
    @Transactional(readOnly = true)
    public List<AssignmentResponse> findByTaskId(Long taskId) {
        return assignmentRepository.findByTaskId(taskId);
    }

    /**
     * 팀 ID와 날짜 범위로 Assignment 목록 조회
     */
    @Transactional(readOnly = true)
    public List<AssignmentResponse> findByTeamIdAndDateRange(Long teamId, 
                                                              java.time.OffsetDateTime start, 
                                                              java.time.OffsetDateTime end) {
        return assignmentRepository.findByTeamIdAndDateRange(teamId, start, end);
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

