package com.example.sbb.controller;

import com.example.sbb.controller.support.AuthenticatedUserResolver;
import com.example.sbb.dto.request.TaskCreateRequest;
import com.example.sbb.dto.request.TaskUpdateRequest;
import com.example.sbb.dto.response.TaskResponse;
import com.example.sbb.service.SlotLockService;
import com.example.sbb.service.TaskService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import jakarta.validation.Valid;
import java.time.Duration;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/tasks")
@Tag(name = "Task API", description = "작업(Task) 생성 및 조회/수정/삭제 API")
public class TaskController {
    private final TaskService taskService;
    private final SlotLockService slotLockService;
    private static final Duration TASK_LOCK_TTL = Duration.ofMinutes(2);

    public TaskController(TaskService taskService, SlotLockService slotLockService) {
        this.taskService = taskService;
        this.slotLockService = slotLockService;
    }

    @PostMapping
    @Operation(summary = "작업 생성", description = "새로운 작업을 생성합니다.")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "작업 생성 성공"),
        @ApiResponse(responseCode = "400", description = "요청 검증 실패")
    })
    public ResponseEntity<TaskResponse> createTask(@Valid @RequestBody TaskCreateRequest request) {
        Long userId = AuthenticatedUserResolver.requireUserId();
        String slotKey = buildTaskCreateSlotKey(request);
        if (!slotLockService.tryLock(slotKey, userId, TASK_LOCK_TTL)) {
            return ResponseEntity.status(HttpStatus.CONFLICT).build();
        }
        try {
            TaskResponse response = taskService.createTask(request);
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } finally {
            slotLockService.releaseLock(slotKey, userId);
        }
    }

    @GetMapping("/{id}")
    @Operation(summary = "작업 단건 조회", description = "작업 ID로 단일 작업 정보를 조회합니다.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "조회 성공"),
        @ApiResponse(responseCode = "404", description = "작업을 찾을 수 없음")
    })
    public ResponseEntity<TaskResponse> getTask(@Parameter(description = "작업 ID", example = "1") @PathVariable Long id) {
        return ResponseEntity.ok(taskService.findById(id));
    }

    @GetMapping("/team/{teamId}")
    @Operation(summary = "팀별 작업 목록", description = "팀 ID 기준으로 모든 작업을 조회합니다.")
    public ResponseEntity<List<TaskResponse>> tasksByTeam(@Parameter(description = "팀 ID", example = "1") @PathVariable Long teamId) {
        return ResponseEntity.ok(taskService.findByTeamId(teamId));
    }

    @GetMapping("/assignee/{assigneeId}")
    @Operation(summary = "담당자별 작업 목록", description = "담당자 ID 기준으로 모든 작업을 조회합니다.")
    public ResponseEntity<List<TaskResponse>> tasksByAssignee(@Parameter(description = "담당자 ID", example = "2") @PathVariable Long assigneeId) {
        return ResponseEntity.ok(taskService.findByAssigneeId(assigneeId));
    }

    @PutMapping("/{id}")
    @Operation(summary = "작업 수정", description = "작업 ID로 작업 정보를 수정합니다.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "수정 성공"),
        @ApiResponse(responseCode = "404", description = "작업을 찾을 수 없음")
    })
    public ResponseEntity<TaskResponse> updateTask(
            @Parameter(description = "작업 ID", example = "1") @PathVariable Long id,
            @Valid @RequestBody TaskUpdateRequest request) {
        Long userId = AuthenticatedUserResolver.requireUserId();
        String slotKey = buildTaskUpdateSlotKey(id);
        if (!slotLockService.tryLock(slotKey, userId, TASK_LOCK_TTL)) {
            return ResponseEntity.status(HttpStatus.CONFLICT).build();
        }
        try {
            TaskResponse response = taskService.updateTask(id, request);
            return ResponseEntity.ok(response);
        } finally {
            slotLockService.releaseLock(slotKey, userId);
        }
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "작업 삭제", description = "작업 ID로 작업을 삭제합니다.")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "삭제 성공"),
        @ApiResponse(responseCode = "404", description = "작업을 찾을 수 없음")
    })
    public ResponseEntity<Void> deleteTask(@Parameter(description = "작업 ID", example = "1") @PathVariable Long id) {
        Long userId = AuthenticatedUserResolver.requireUserId();
        String slotKey = buildTaskUpdateSlotKey(id);
        if (!slotLockService.tryLock(slotKey, userId, TASK_LOCK_TTL)) {
            return ResponseEntity.status(HttpStatus.CONFLICT).build();
        }
        try {
            taskService.deleteTask(id);
            return ResponseEntity.noContent().build();
        } finally {
            slotLockService.releaseLock(slotKey, userId);
        }
    }

    private String buildTaskCreateSlotKey(TaskCreateRequest request) {
        if (request.getAssigneeId() != null) {
            return "task:assignee:" + request.getAssigneeId();
        }
        return "task:team:" + request.getTeamId();
    }

    private String buildTaskUpdateSlotKey(Long taskId) {
        return "task:id:" + taskId;
    }
}


