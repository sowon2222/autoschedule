package com.example.sbb.controller;

import com.example.sbb.dto.event.CalendarEventMessage;
import com.example.sbb.dto.event.CollaborationNotificationMessage;
import com.example.sbb.dto.event.ConflictAlertMessage;
import com.example.sbb.dto.event.ScheduleProgressMessage;
import com.example.sbb.dto.event.TaskEventMessage;
import com.example.sbb.dto.response.CalendarEventResponse;
import com.example.sbb.dto.response.TaskResponse;
import com.example.sbb.service.CollaborationEventPublisher;
import com.example.sbb.controller.support.AuthenticatedUserResolver;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * WebSocket STOMP 테스트를 위한 컨트롤러.
 * 테스트용 메시지를 발행할 수 있는 API를 제공합니다.
 */
@RestController
@RequestMapping("/api/test/ws")
@Tag(name = "WebSocket Test API", description = "WebSocket STOMP 테스트용 메시지 발행 API")
public class WebSocketTestController {

    private final CollaborationEventPublisher eventPublisher;
    private final ObjectMapper objectMapper;

    public WebSocketTestController(CollaborationEventPublisher eventPublisher, ObjectMapper objectMapper) {
        this.eventPublisher = eventPublisher;
        this.objectMapper = objectMapper;
    }

    @PostMapping("/publish")
    @Operation(summary = "테스트 메시지 발행", description = "지정된 타입의 테스트 메시지를 WebSocket 토픽에 발행합니다.")
    public ResponseEntity<String> publishTestMessage(@RequestBody PublishRequest request) {
        try {
            Long userId = AuthenticatedUserResolver.requireUserId();
            Long teamId = request.teamId() != null ? request.teamId() : 1L;

            switch (request.type().toLowerCase()) {
                case "task" -> publishTaskEvent(teamId, request.payload());
                case "calendar" -> publishCalendarEvent(teamId, request.payload());
                case "notification" -> publishNotification(teamId, request.payload());
                case "conflict" -> publishConflictAlert(teamId, request.payload());
                case "schedule" -> publishScheduleProgress(teamId, request.payload());
                default -> {
                    return ResponseEntity.badRequest()
                            .body("지원하지 않는 메시지 타입: " + request.type());
                }
            }

            return ResponseEntity.ok("메시지가 성공적으로 발행되었습니다.");
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("메시지 발행 실패: " + e.getMessage());
        }
    }

    private void publishTaskEvent(Long teamId, Map<String, Object> payload) {
        String action = (String) payload.getOrDefault("action", "CREATED");
        Long taskId = payload.get("taskId") != null 
                ? Long.valueOf(payload.get("taskId").toString()) 
                : System.currentTimeMillis();

        TaskResponse task = null;
        if (payload.get("task") != null) {
            task = objectMapper.convertValue(payload.get("task"), TaskResponse.class);
        }

        TaskEventMessage message;
        if ("DELETED".equals(action)) {
            message = TaskEventMessage.deleted(taskId, teamId);
        } else if ("UPDATED".equals(action) && task != null) {
            message = TaskEventMessage.updated(task);
        } else {
            if (task == null) {
                task = createDefaultTask(taskId, teamId);
            }
            message = TaskEventMessage.created(task);
        }

        eventPublisher.publishTaskEvent(message);
    }

    private void publishCalendarEvent(Long teamId, Map<String, Object> payload) {
        String action = (String) payload.getOrDefault("action", "CREATED");
        Long eventId = payload.get("eventId") != null 
                ? Long.valueOf(payload.get("eventId").toString()) 
                : System.currentTimeMillis();

        CalendarEventResponse event = null;
        if (payload.get("event") != null) {
            event = objectMapper.convertValue(payload.get("event"), CalendarEventResponse.class);
        }

        CalendarEventMessage message;
        if ("DELETED".equals(action)) {
            message = CalendarEventMessage.deleted(teamId, eventId);
        } else if ("UPDATED".equals(action) && event != null) {
            message = CalendarEventMessage.updated(event);
        } else {
            if (event == null) {
                event = createDefaultCalendarEvent(eventId, teamId);
            }
            message = CalendarEventMessage.created(event);
        }

        eventPublisher.publishCalendarEvent(message);
    }

    private void publishNotification(Long teamId, Map<String, Object> payload) {
        String scope = (String) payload.getOrDefault("scope", "TEAM");
        Long targetId = payload.get("targetId") != null 
                ? Long.valueOf(payload.get("targetId").toString()) 
                : teamId;
        String category = (String) payload.getOrDefault("category", "TEST");
        String title = (String) payload.getOrDefault("title", "테스트 알림");
        String content = (String) payload.getOrDefault("content", "이것은 테스트 알림입니다.");

        CollaborationNotificationMessage message;
        if ("USER".equalsIgnoreCase(scope)) {
            message = CollaborationNotificationMessage.user(teamId, targetId, category, title, content);
        } else {
            message = CollaborationNotificationMessage.team(teamId, category, title, content);
        }

        eventPublisher.publishNotification(message);
    }

    private void publishConflictAlert(Long teamId, Map<String, Object> payload) {
        Long sourceId = payload.get("sourceId") != null 
                ? Long.valueOf(payload.get("sourceId").toString()) 
                : System.currentTimeMillis();

        CalendarEventResponse source = null;
        if (payload.get("source") != null) {
            source = objectMapper.convertValue(payload.get("source"), CalendarEventResponse.class);
        } else {
            source = createDefaultCalendarEvent(sourceId, teamId);
        }

        List<CalendarEventResponse> conflicts = new ArrayList<>();
        if (payload.get("conflicts") != null && payload.get("conflicts") instanceof List) {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> conflictList = (List<Map<String, Object>>) payload.get("conflicts");
            for (Map<String, Object> conflictMap : conflictList) {
                conflicts.add(objectMapper.convertValue(conflictMap, CalendarEventResponse.class));
            }
        }

        String messageText = (String) payload.getOrDefault("message", "스케줄 충돌이 감지되었습니다.");
        ConflictAlertMessage message = ConflictAlertMessage.calendarConflict(teamId, source, conflicts, messageText);

        eventPublisher.publishConflictAlert(message);
    }

    private void publishScheduleProgress(Long teamId, Map<String, Object> payload) {
        String status = (String) payload.getOrDefault("status", "PROGRESS");
        Integer progress = payload.get("progress") != null 
                ? Integer.valueOf(payload.get("progress").toString()) 
                : 50;
        String messageText = (String) payload.getOrDefault("message", "스케줄 최적화 진행 중...");

        ScheduleProgressMessage message;
        if ("COMPLETED".equals(status)) {
            message = ScheduleProgressMessage.completed(teamId, null);
        } else if ("FAILED".equals(status)) {
            message = ScheduleProgressMessage.failed(teamId, messageText);
        } else {
            message = ScheduleProgressMessage.progress(teamId, progress, messageText);
        }

        eventPublisher.publishScheduleProgress(message);
    }

    private TaskResponse createDefaultTask(Long taskId, Long teamId) {
        TaskResponse task = new TaskResponse();
        task.setId(taskId);
        task.setTeamId(teamId);
        task.setTitle("테스트 작업");
        task.setDurationMin(60);
        task.setPriority(3);
        task.setCreatedAt(OffsetDateTime.now());
        return task;
    }

    private CalendarEventResponse createDefaultCalendarEvent(Long eventId, Long teamId) {
        CalendarEventResponse event = new CalendarEventResponse();
        event.setId(eventId);
        event.setTeamId(teamId);
        event.setTitle("테스트 이벤트");
        event.setStartsAt(OffsetDateTime.now());
        event.setEndsAt(OffsetDateTime.now().plusHours(1));
        event.setCreatedAt(OffsetDateTime.now());
        return event;
    }

    public record PublishRequest(
            String type,
            Long teamId,
            Map<String, Object> payload
    ) {}
}

