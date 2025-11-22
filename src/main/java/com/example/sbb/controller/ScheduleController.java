package com.example.sbb.controller;

import com.example.sbb.controller.support.AuthenticatedUserResolver;
import com.example.sbb.dto.request.ScheduleCreateRequest;
import com.example.sbb.dto.response.ScheduleResponse;
import com.example.sbb.service.ScheduleService;
import com.example.sbb.service.SchedulingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/schedules")
@Tag(name = "Schedule API", description = "스케줄 생성 및 조회 API")
public class ScheduleController {

    private final SchedulingService schedulingService;
    private final ScheduleService scheduleService;

    public ScheduleController(SchedulingService schedulingService, ScheduleService scheduleService) {
        this.schedulingService = schedulingService;
        this.scheduleService = scheduleService;
    }

    @PostMapping("/generate")
    @Operation(summary = "스케줄 생성", description = "자동 스케줄링 알고리즘으로 스케줄을 생성합니다. 비동기로 실행되며 진행률은 WebSocket으로 전송됩니다.")
    @ApiResponses({
        @ApiResponse(responseCode = "202", description = "스케줄 생성 요청 수락 (비동기 처리)"),
        @ApiResponse(responseCode = "400", description = "요청 검증 실패")
    })
    public ResponseEntity<Void> generateSchedule(@Valid @RequestBody ScheduleCreateRequest request) {
        Long userId = AuthenticatedUserResolver.requireUserId();
        
        // 비동기로 스케줄 생성 시작
        schedulingService.generateSchedule(
            request.getTeamId(),
            request.getRangeStart(),
            request.getRangeEnd(),
            userId
        );
        
        return ResponseEntity.status(HttpStatus.ACCEPTED).build();
    }

    @GetMapping("/{id}")
    @Operation(summary = "스케줄 단건 조회", description = "스케줄 ID로 단일 스케줄 정보를 조회합니다.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "조회 성공"),
        @ApiResponse(responseCode = "404", description = "스케줄을 찾을 수 없음")
    })
    public ResponseEntity<ScheduleResponse> getSchedule(
            @Parameter(description = "스케줄 ID", example = "1") @PathVariable Long id) {
        ScheduleResponse response = scheduleService.getScheduleById(id);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/team/{teamId}")
    @Operation(summary = "팀의 스케줄 목록 조회", description = "팀 ID로 해당 팀의 모든 스케줄을 조회합니다.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "조회 성공")
    })
    public ResponseEntity<List<ScheduleResponse>> getSchedulesByTeam(
            @Parameter(description = "팀 ID", example = "1") @PathVariable Long teamId) {
        List<ScheduleResponse> responses = scheduleService.getSchedulesByTeamId(teamId);
        return ResponseEntity.ok(responses);
    }

    @GetMapping("/team/{teamId}/latest")
    @Operation(summary = "팀의 최신 스케줄 조회", description = "팀 ID로 해당 팀의 최신 스케줄을 조회합니다.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "조회 성공"),
        @ApiResponse(responseCode = "404", description = "스케줄을 찾을 수 없음")
    })
    public ResponseEntity<ScheduleResponse> getLatestScheduleByTeam(
            @Parameter(description = "팀 ID", example = "1") @PathVariable Long teamId) {
        ScheduleResponse response = scheduleService.getLatestScheduleByTeamId(teamId);
        if (response == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "스케줄 삭제", description = "스케줄 ID로 스케줄을 삭제합니다.")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "삭제 성공"),
        @ApiResponse(responseCode = "404", description = "스케줄을 찾을 수 없음")
    })
    public ResponseEntity<Void> deleteSchedule(
            @Parameter(description = "스케줄 ID", example = "1") @PathVariable Long id) {
        scheduleService.deleteSchedule(id);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/team/{teamId}/all")
    @Operation(summary = "팀의 모든 스케줄 삭제", description = "팀 ID로 해당 팀의 모든 스케줄을 삭제합니다.")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "삭제 성공")
    })
    public ResponseEntity<Void> deleteAllSchedulesByTeam(
            @Parameter(description = "팀 ID", example = "1") @PathVariable Long teamId) {
        scheduleService.deleteAllSchedulesByTeamId(teamId);
        return ResponseEntity.noContent().build();
    }
}

