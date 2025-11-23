package com.example.sbb.controller;

import com.example.sbb.controller.support.AuthenticatedUserResolver;
import com.example.sbb.dto.request.WorkHourCreateRequest;
import com.example.sbb.dto.request.WorkHourUpdateRequest;
import com.example.sbb.dto.response.WorkHourResponse;
import com.example.sbb.service.SlotLockService;
import com.example.sbb.service.WorkHourService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.Duration;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
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
    @Operation(summary = "팀 근무시간 조회", description = "팀의 모든 근무시간 설정을 조회합니다.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "조회 성공")
    })
    public ResponseEntity<List<WorkHourResponse>> list(
        @Parameter(description = "팀 ID", example = "1") @PathVariable Long teamId) {
        return ResponseEntity.ok(workHourService.getTeamWorkHours(teamId));
    }

    @GetMapping("/{id}")
    @Operation(summary = "근무시간 상세 조회", description = "특정 근무시간 설정을 조회합니다.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "조회 성공"),
        @ApiResponse(responseCode = "404", description = "근무시간을 찾을 수 없음")
    })
    public ResponseEntity<WorkHourResponse> get(
        @Parameter(description = "팀 ID", example = "1") @PathVariable Long teamId,
        @Parameter(description = "근무시간 ID", example = "1") @PathVariable Long id) {
        return ResponseEntity.ok(workHourService.getWorkHour(id));
    }

    @PostMapping
    @Operation(summary = "근무시간 생성", description = "새로운 근무시간 설정을 생성합니다. (단일 또는 여러 개)")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "생성 성공"),
        @ApiResponse(responseCode = "400", description = "요청 검증 실패 또는 중복"),
        @ApiResponse(responseCode = "409", description = "다른 사용자가 편집 중")
    })
    public ResponseEntity<?> create(
        @Parameter(description = "팀 ID", example = "1") @PathVariable Long teamId,
        @RequestBody Object requestBody) {
        
        Long userId = AuthenticatedUserResolver.requireUserId();
        String slotKey = buildWorkHourSlotKey(teamId);
        if (!slotLockService.tryLock(slotKey, userId, WORK_HOUR_LOCK_TTL)) {
            return ResponseEntity.status(HttpStatus.CONFLICT).build();
        }
        try {
            // 배열인지 단일 객체인지 확인
            if (requestBody instanceof java.util.List) {
                @SuppressWarnings("unchecked")
                List<Object> rawList = (List<Object>) requestBody;
                
                // LinkedHashMap을 WorkHourCreateRequest로 변환
                List<WorkHourCreateRequest> requests = rawList.stream()
                    .map(item -> {
                        if (item instanceof WorkHourCreateRequest) {
                            return (WorkHourCreateRequest) item;
                        } else if (item instanceof java.util.Map) {
                            @SuppressWarnings("unchecked")
                            java.util.Map<String, Object> map = (java.util.Map<String, Object>) item;
                            WorkHourCreateRequest req = new WorkHourCreateRequest();
                            req.setTeamId(map.get("teamId") != null ? Long.valueOf(map.get("teamId").toString()) : teamId);
                            req.setUserId(map.get("userId") != null ? Long.valueOf(map.get("userId").toString()) : null);
                            req.setDow(map.get("dow") != null ? Integer.valueOf(map.get("dow").toString()) : null);
                            req.setStartMin(map.get("startMin") != null ? Integer.valueOf(map.get("startMin").toString()) : null);
                            req.setEndMin(map.get("endMin") != null ? Integer.valueOf(map.get("endMin").toString()) : null);
                            return req;
                        } else {
                            throw new IllegalArgumentException("Invalid request body format");
                        }
                    })
                    .collect(java.util.stream.Collectors.toList());
                
                // teamId 설정 및 validation
                for (WorkHourCreateRequest req : requests) {
                    req.setTeamId(teamId);
                }
                List<WorkHourResponse> responses = workHourService.createWorkHours(teamId, requests);
                return ResponseEntity.status(HttpStatus.CREATED).body(responses);
            } else {
                WorkHourCreateRequest request;
                if (requestBody instanceof WorkHourCreateRequest) {
                    request = (WorkHourCreateRequest) requestBody;
                } else if (requestBody instanceof java.util.Map) {
                    @SuppressWarnings("unchecked")
                    java.util.Map<String, Object> map = (java.util.Map<String, Object>) requestBody;
                    request = new WorkHourCreateRequest();
                    request.setTeamId(map.get("teamId") != null ? Long.valueOf(map.get("teamId").toString()) : teamId);
                    request.setUserId(map.get("userId") != null ? Long.valueOf(map.get("userId").toString()) : null);
                    request.setDow(map.get("dow") != null ? Integer.valueOf(map.get("dow").toString()) : null);
                    request.setStartMin(map.get("startMin") != null ? Integer.valueOf(map.get("startMin").toString()) : null);
                    request.setEndMin(map.get("endMin") != null ? Integer.valueOf(map.get("endMin").toString()) : null);
                } else {
                    return ResponseEntity.badRequest().build();
                }
                request.setTeamId(teamId);
                WorkHourResponse response = workHourService.createWorkHour(request);
                return ResponseEntity.status(HttpStatus.CREATED).body(response);
            }
        } finally {
            slotLockService.releaseLock(slotKey, userId);
        }
    }

    @PutMapping("/{id}")
    @Operation(summary = "근무시간 수정", description = "기존 근무시간 설정을 수정합니다.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "수정 성공"),
        @ApiResponse(responseCode = "400", description = "요청 검증 실패 또는 중복"),
        @ApiResponse(responseCode = "404", description = "근무시간을 찾을 수 없음"),
        @ApiResponse(responseCode = "409", description = "다른 사용자가 편집 중")
    })
    public ResponseEntity<WorkHourResponse> update(
        @Parameter(description = "팀 ID", example = "1") @PathVariable Long teamId,
        @Parameter(description = "근무시간 ID", example = "1") @PathVariable Long id,
        @Valid @RequestBody WorkHourUpdateRequest request) {
        Long userId = AuthenticatedUserResolver.requireUserId();
        String slotKey = buildWorkHourSlotKey(teamId);
        if (!slotLockService.tryLock(slotKey, userId, WORK_HOUR_LOCK_TTL)) {
            return ResponseEntity.status(HttpStatus.CONFLICT).build();
        }
        try {
            WorkHourResponse response = workHourService.updateWorkHour(id, request);
            return ResponseEntity.ok(response);
        } finally {
            slotLockService.releaseLock(slotKey, userId);
        }
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "근무시간 삭제", description = "근무시간 설정을 삭제합니다.")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "삭제 성공"),
        @ApiResponse(responseCode = "404", description = "근무시간을 찾을 수 없음"),
        @ApiResponse(responseCode = "409", description = "다른 사용자가 편집 중")
    })
    public ResponseEntity<Void> delete(
        @Parameter(description = "팀 ID", example = "1") @PathVariable Long teamId,
        @Parameter(description = "근무시간 ID", example = "1") @PathVariable Long id) {
        Long userId = AuthenticatedUserResolver.requireUserId();
        String slotKey = buildWorkHourSlotKey(teamId);
        if (!slotLockService.tryLock(slotKey, userId, WORK_HOUR_LOCK_TTL)) {
            return ResponseEntity.status(HttpStatus.CONFLICT).build();
        }
        try {
            workHourService.deleteWorkHour(id);
            return ResponseEntity.noContent().build();
        } finally {
            slotLockService.releaseLock(slotKey, userId);
        }
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

