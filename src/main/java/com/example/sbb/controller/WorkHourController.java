package com.example.sbb.controller;

import com.example.sbb.controller.support.AuthenticatedUserResolver;
import com.example.sbb.dto.request.WorkHourUpdateRequest;
import com.example.sbb.dto.response.WorkHourResponse;
import com.example.sbb.service.SlotLockService;
import com.example.sbb.service.WorkHourService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Duration;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/teams/{teamId}/work-hours")
@Tag(name = "Work Hour API", description = "팀 근무시간 조회 및 업데이트 API")
public class WorkHourController {

    private static final Duration WORK_HOUR_LOCK_TTL = Duration.ofMinutes(5);
    private final WorkHourService workHourService;
    private final SlotLockService slotLockService;

    public WorkHourController(WorkHourService workHourService, SlotLockService slotLockService) {
        this.workHourService = workHourService;
        this.slotLockService = slotLockService;
    }

    @GetMapping
    @Operation(summary = "팀 근무시간 조회", description = "팀의 근무시간 설정을 조회합니다.")
    public ResponseEntity<List<WorkHourResponse>> list(
        @Parameter(description = "팀 ID", example = "1") @PathVariable Long teamId) {
        return ResponseEntity.ok(workHourService.getTeamWorkHours(teamId));
    }

    @PutMapping
    @Operation(summary = "팀 근무시간 갱신", description = "팀 근무시간 설정을 일괄 갱신합니다.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "갱신 성공"),
        @ApiResponse(responseCode = "409", description = "다른 사용자가 편집 중")
    })
    public ResponseEntity<List<WorkHourResponse>> upsert(
        @Parameter(description = "팀 ID", example = "1") @PathVariable Long teamId,
        @RequestBody List<WorkHourUpdateRequest> updates) {
        Long userId = AuthenticatedUserResolver.requireUserId();
        String slotKey = buildWorkHourSlotKey(teamId);
        if (!slotLockService.tryLock(slotKey, userId, WORK_HOUR_LOCK_TTL)) {
            return ResponseEntity.status(HttpStatus.CONFLICT).build();
        }
        try {
            List<WorkHourResponse> responses = workHourService.upsertWorkHours(teamId, updates);
            return ResponseEntity.ok(responses);
        } finally {
            slotLockService.releaseLock(slotKey, userId);
        }
    }

    private String buildWorkHourSlotKey(Long teamId) {
        return "work-hours:team:" + teamId;
    }
}

