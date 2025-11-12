package com.example.sbb.controller;

import com.example.sbb.controller.support.AuthenticatedUserResolver;
import com.example.sbb.dto.request.CalendarEventCreateRequest;
import com.example.sbb.dto.request.CalendarEventUpdateRequest;
import com.example.sbb.dto.response.CalendarEventResponse;
import com.example.sbb.service.CalendarEventService;
import com.example.sbb.service.SlotLockService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/events")
@Tag(name = "Calendar Event API", description = "캘린더 이벤트 생성 및 조회/수정/삭제 API")
public class CalendarEventController {
    private final CalendarEventService calendarEventService;
    private final SlotLockService slotLockService;
    private static final Duration EVENT_LOCK_TTL = Duration.ofMinutes(3);

    public CalendarEventController(CalendarEventService calendarEventService, SlotLockService slotLockService) {
        this.calendarEventService = calendarEventService;
        this.slotLockService = slotLockService;
    }

    @PostMapping
    @Operation(summary = "이벤트 생성", description = "새로운 캘린더 이벤트를 생성합니다.")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "이벤트 생성 성공"),
        @ApiResponse(responseCode = "400", description = "요청 검증 실패")
    })
    public ResponseEntity<CalendarEventResponse> create(@Valid @RequestBody CalendarEventCreateRequest request) {
        Long userId = AuthenticatedUserResolver.requireUserId();
        String slotKey = buildEventCreateSlotKey(request);
        if (!slotLockService.tryLock(slotKey, userId, EVENT_LOCK_TTL)) {
            return ResponseEntity.status(HttpStatus.CONFLICT).build();
        }
        try {
            CalendarEventResponse response = calendarEventService.createEvent(request);
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } finally {
            slotLockService.releaseLock(slotKey, userId);
        }
    }

    @GetMapping("/{id}")
    @Operation(summary = "이벤트 단건 조회", description = "이벤트 ID로 단일 이벤트 정보를 조회합니다.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "조회 성공"),
        @ApiResponse(responseCode = "404", description = "이벤트를 찾을 수 없음")
    })
    public ResponseEntity<CalendarEventResponse> get(@Parameter(description = "이벤트 ID", example = "1") @PathVariable Long id) {
        return ResponseEntity.ok(calendarEventService.findById(id));
    }

    @GetMapping("/team/{teamId}")
    @Operation(summary = "팀별 이벤트 목록", description = "팀 ID 기준으로 모든 이벤트를 조회합니다.")
    public ResponseEntity<List<CalendarEventResponse>> byTeam(@Parameter(description = "팀 ID", example = "1") @PathVariable Long teamId) {
        return ResponseEntity.ok(calendarEventService.findByTeam(teamId));
    }

    @GetMapping("/team/{teamId}/range")
    @Operation(summary = "팀 이벤트 기간 조회", description = "팀 ID와 기간(시작/종료)으로 이벤트를 조회합니다.")
    public ResponseEntity<List<CalendarEventResponse>> byRange(
        @Parameter(description = "팀 ID", example = "1") @PathVariable Long teamId,
        @Parameter(description = "조회 시작 시각", example = "2025-12-01T00:00:00+09:00") @RequestParam java.time.OffsetDateTime start,
        @Parameter(description = "조회 종료 시각", example = "2025-12-07T23:59:59+09:00") @RequestParam java.time.OffsetDateTime end
    ) {
        return ResponseEntity.ok(calendarEventService.findByTeamAndRange(teamId, start, end));
    }

    @GetMapping("/user/{userId}")
    @Operation(summary = "사용자 이벤트 목록", description = "이벤트 소유자 ID 기준으로 모든 이벤트를 조회합니다.")
    public ResponseEntity<List<CalendarEventResponse>> byUser(@Parameter(description = "사용자 ID", example = "2") @PathVariable Long userId) {
        return ResponseEntity.ok(calendarEventService.findByOwner(userId));
    }

    @GetMapping("/user/{userId}/range")
    @Operation(summary = "사용자 이벤트 기간 조회", description = "사용자 ID와 기간(시작/종료)으로 이벤트를 조회합니다.")
    public ResponseEntity<List<CalendarEventResponse>> byUserAndRange(
        @Parameter(description = "사용자 ID", example = "2") @PathVariable Long userId,
        @Parameter(description = "조회 시작 시각", example = "2025-12-01T00:00:00+09:00") @RequestParam java.time.OffsetDateTime start,
        @Parameter(description = "조회 종료 시각", example = "2025-12-07T23:59:59+09:00") @RequestParam java.time.OffsetDateTime end
    ) {
        return ResponseEntity.ok(calendarEventService.findByOwnerAndRange(userId, start, end));
    }

    @PutMapping("/{id}")
    @Operation(summary = "이벤트 수정", description = "이벤트 ID로 이벤트 정보를 수정합니다.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "수정 성공"),
        @ApiResponse(responseCode = "404", description = "이벤트를 찾을 수 없음")
    })
    public ResponseEntity<CalendarEventResponse> update(
        @Parameter(description = "이벤트 ID", example = "1") @PathVariable Long id,
        @Valid @RequestBody CalendarEventUpdateRequest request) {
        Long userId = AuthenticatedUserResolver.requireUserId();
        String slotKey = buildEventUpdateSlotKey(id);
        if (!slotLockService.tryLock(slotKey, userId, EVENT_LOCK_TTL)) {
            return ResponseEntity.status(HttpStatus.CONFLICT).build();
        }
        try {
            CalendarEventResponse response = calendarEventService.updateEvent(id, request);
            return ResponseEntity.ok(response);
        } finally {
            slotLockService.releaseLock(slotKey, userId);
        }
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "이벤트 삭제", description = "이벤트 ID로 이벤트를 삭제합니다.")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "삭제 성공"),
        @ApiResponse(responseCode = "404", description = "이벤트를 찾을 수 없음")
    })
    public ResponseEntity<Void> delete(@Parameter(description = "이벤트 ID", example = "1") @PathVariable Long id) {
        Long userId = AuthenticatedUserResolver.requireUserId();
        String slotKey = buildEventUpdateSlotKey(id);
        if (!slotLockService.tryLock(slotKey, userId, EVENT_LOCK_TTL)) {
            return ResponseEntity.status(HttpStatus.CONFLICT).build();
        }
        try {
            calendarEventService.deleteEvent(id);
            return ResponseEntity.noContent().build();
        } finally {
            slotLockService.releaseLock(slotKey, userId);
        }
    }

    private String buildEventCreateSlotKey(CalendarEventCreateRequest request) {
        OffsetDateTime start = request.getStartsAt() != null ? request.getStartsAt() : OffsetDateTime.now();
        OffsetDateTime end = request.getEndsAt() != null ? request.getEndsAt() : start;
        return "calendar:event:create:team:" + request.getTeamId()
            + ":window:" + start.toInstant().toEpochMilli() + ":" + end.toInstant().toEpochMilli();
    }

    private String buildEventUpdateSlotKey(Long eventId) {
        return "calendar:event:id:" + eventId;
    }
}


