package com.example.sbb.controller;

import com.example.sbb.dto.response.AssignmentResponse;
import com.example.sbb.service.AssignmentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/assignments")
@Tag(name = "Assignment API", description = "Assignment 조회 API")
public class AssignmentController {

    private final AssignmentService assignmentService;

    public AssignmentController(AssignmentService assignmentService) {
        this.assignmentService = assignmentService;
    }

    @GetMapping("/team/{teamId}")
    @Operation(summary = "팀의 Assignment 목록 조회", description = "팀 ID와 날짜 범위로 Assignment 목록을 조회합니다.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "조회 성공")
    })
    public ResponseEntity<List<AssignmentResponse>> getAssignmentsByTeam(
            @Parameter(description = "팀 ID", example = "1") @PathVariable Long teamId,
            @Parameter(description = "시작 날짜", example = "2025-11-01T00:00:00Z") 
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime start,
            @Parameter(description = "종료 날짜", example = "2025-11-30T23:59:59Z") 
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime end) {
        
        List<AssignmentResponse> assignments;
        if (start != null && end != null) {
            assignments = assignmentService.findByTeamIdAndDateRange(teamId, start, end);
        } else {
            // 날짜 범위가 없으면 최근 30일
            OffsetDateTime defaultStart = OffsetDateTime.now().minusDays(30);
            OffsetDateTime defaultEnd = OffsetDateTime.now().plusDays(30);
            assignments = assignmentService.findByTeamIdAndDateRange(teamId, defaultStart, defaultEnd);
        }
        
        return ResponseEntity.ok(assignments);
    }

    @GetMapping("/task/{taskId}")
    @Operation(summary = "작업의 Assignment 목록 조회", description = "작업 ID로 Assignment 목록을 조회합니다.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "조회 성공")
    })
    public ResponseEntity<List<AssignmentResponse>> getAssignmentsByTask(
            @Parameter(description = "작업 ID", example = "1") @PathVariable Long taskId) {
        List<AssignmentResponse> assignments = assignmentService.findByTaskId(taskId);
        return ResponseEntity.ok(assignments);
    }

    @GetMapping("/schedule/{scheduleId}")
    @Operation(summary = "스케줄의 Assignment 목록 조회", description = "스케줄 ID로 Assignment 목록을 조회합니다.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "조회 성공")
    })
    public ResponseEntity<List<AssignmentResponse>> getAssignmentsBySchedule(
            @Parameter(description = "스케줄 ID", example = "1") @PathVariable Long scheduleId) {
        List<AssignmentResponse> assignments = assignmentService.findByScheduleId(scheduleId);
        return ResponseEntity.ok(assignments);
    }
}

