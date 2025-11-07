package com.example.sbb.service;

import com.example.sbb.domain.Task;
import com.example.sbb.domain.Team;
import com.example.sbb.domain.User;
import com.example.sbb.dto.request.TaskCreateRequest;
import com.example.sbb.dto.request.TaskUpdateRequest;
import com.example.sbb.dto.response.TaskResponse;
import com.example.sbb.repository.TaskRepository;
import com.example.sbb.repository.TeamRepository;
import com.example.sbb.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class TaskService {
    private final TaskRepository taskRepository;
    private final TeamRepository teamRepository;
    private final UserRepository userRepository;

    public TaskService(TaskRepository taskRepository, TeamRepository teamRepository, UserRepository userRepository) {
        this.taskRepository = taskRepository;
        this.teamRepository = teamRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public TaskResponse createTask(TaskCreateRequest request) {
        // 우선순위 검증 (1-5 범위)
        Integer priority = request.getPriority() != null ? request.getPriority() : 3;
        if (priority < 1 || priority > 5) {
            throw new IllegalArgumentException("우선순위는 1부터 5 사이의 값이어야 합니다. 입력값: " + priority);
        }
        
        // 마감일 검증 (과거 날짜가 아닌지 확인)
        OffsetDateTime now = OffsetDateTime.now();
        if (request.getDueAt() != null && request.getDueAt().isBefore(now)) {
            throw new IllegalArgumentException("마감일은 현재 시간 이후여야 합니다. 입력값: " + request.getDueAt());
        }
        
        Task task = new Task();
        task.setTitle(request.getTitle());
        Team team = teamRepository.findById(request.getTeamId())
            .orElseThrow(() -> new IllegalArgumentException("팀을 찾을 수 없습니다: " + request.getTeamId()));
        task.setTeam(team);
        if (request.getAssigneeId() != null) {
            User assignee = userRepository.findById(request.getAssigneeId())
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다: " + request.getAssigneeId()));
            task.setAssignee(assignee);
        }
        task.setDurationMin(request.getDurationMin());
        task.setPriority(priority);
        task.setDueAt(request.getDueAt());
        task.setSplittable(request.getSplittable() == null || request.getSplittable());
        task.setTags(request.getTags());
        task.setCreatedAt(OffsetDateTime.now());
        task.setUpdatedAt(OffsetDateTime.now());
        Task saved = taskRepository.save(task);
        return toResponse(saved);
    }

    public TaskResponse findById(Long id) {
        Task task = taskRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("작업을 찾을 수 없습니다: " + id));
        return toResponse(task);
    }

    public List<TaskResponse> findByTeamId(Long teamId) {
        return taskRepository.findByTeam_Id(teamId).stream().map(this::toResponse).collect(Collectors.toList());
    }

    public List<TaskResponse> findByAssigneeId(Long assigneeId) {
        return taskRepository.findByAssignee_Id(assigneeId).stream().map(this::toResponse).collect(Collectors.toList());
    }

    @Transactional
    public TaskResponse updateTask(Long id, TaskUpdateRequest request) {
        Task task = taskRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("작업을 찾을 수 없습니다: " + id));
        
        if (request.getTitle() != null) task.setTitle(request.getTitle());
        if (request.getAssigneeId() != null) {
            User assignee = userRepository.findById(request.getAssigneeId())
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다: " + request.getAssigneeId()));
            task.setAssignee(assignee);
        }
        if (request.getDurationMin() != null) task.setDurationMin(request.getDurationMin());
        
        // 우선순위 검증 (1-5 범위)
        if (request.getPriority() != null) {
            if (request.getPriority() < 1 || request.getPriority() > 5) {
                throw new IllegalArgumentException("우선순위는 1부터 5 사이의 값이어야 합니다. 입력값: " + request.getPriority());
            }
            task.setPriority(request.getPriority());
        }
        
        // 마감일 검증 (과거 날짜가 아닌지 확인)
        if (request.getDueAt() != null) {
            OffsetDateTime now = OffsetDateTime.now();
            if (request.getDueAt().isBefore(now)) {
                throw new IllegalArgumentException("마감일은 현재 시간 이후여야 합니다. 입력값: " + request.getDueAt());
            }
            task.setDueAt(request.getDueAt());
        }
        
        if (request.getSplittable() != null) task.setSplittable(request.getSplittable());
        if (request.getTags() != null) task.setTags(request.getTags());
        task.setUpdatedAt(OffsetDateTime.now());
        Task saved = taskRepository.save(task);
        return toResponse(saved);
    }

    @Transactional
    public void deleteTask(Long id) {
        if (!taskRepository.existsById(id)) throw new IllegalArgumentException("작업을 찾을 수 없습니다: " + id);
        taskRepository.deleteById(id);
    }

    private TaskResponse toResponse(Task t) {
        TaskResponse r = new TaskResponse();
        r.setId(t.getId());
        r.setTitle(t.getTitle());
        r.setDurationMin(t.getDurationMin());
        r.setTeamId(t.getTeam() != null ? t.getTeam().getId() : null);
        r.setAssigneeId(t.getAssignee() != null ? t.getAssignee().getId() : null);
        r.setPriority(t.getPriority());
        r.setDueAt(t.getDueAt());
        r.setSplittable(t.isSplittable());
        r.setTags(t.getTags());
        r.setCreatedAt(t.getCreatedAt());
        r.setUpdatedAt(t.getUpdatedAt());
        return r;
    }
}


