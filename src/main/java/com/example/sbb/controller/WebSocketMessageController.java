package com.example.sbb.controller;

import com.example.sbb.dto.event.ScheduleProgressMessage;
import com.example.sbb.dto.event.TaskEventMessage;
import com.example.sbb.service.CollaborationEventPublisher;
import java.util.Map;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.stereotype.Controller;

@Controller
public class WebSocketMessageController {

    private final CollaborationEventPublisher eventPublisher;

    public WebSocketMessageController(CollaborationEventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
    }

    @MessageMapping("/tasks/update")
    public void relayTaskUpdate(TaskEventMessage message) {
        eventPublisher.publishTaskEvent(message);
    }

    @MessageMapping("/detail/{entityType}/{entityId}")
    public void relayDetailUpdate(@DestinationVariable String entityType,
                                  @DestinationVariable Long entityId,
                                  Map<String, Object> payload) {
        eventPublisher.publishDetailUpdate(entityType, entityId, payload);
    }

    @MessageMapping("/schedules/progress")
    public void relayScheduleProgress(ScheduleProgressMessage message) {
        eventPublisher.publishScheduleProgress(message);
    }
}

