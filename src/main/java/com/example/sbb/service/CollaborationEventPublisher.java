package com.example.sbb.service;

import com.example.sbb.dto.event.CalendarEventMessage;
import com.example.sbb.dto.event.CollaborationNotificationMessage;
import com.example.sbb.dto.event.ConflictAlertMessage;
import com.example.sbb.dto.event.ScheduleProgressMessage;
import com.example.sbb.dto.event.TaskEventMessage;
import com.example.sbb.dto.response.ScheduleResponse;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class CollaborationEventPublisher {

    private final SimpMessagingTemplate messagingTemplate;

    public CollaborationEventPublisher(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    public void publishTaskEvent(TaskEventMessage message) {
        messagingTemplate.convertAndSend("/topic/tasks", message);
        if (message.teamId() != null) {
            messagingTemplate.convertAndSend("/topic/tasks/" + message.teamId(), message);
        }
        if (message.task() != null && message.taskId() != null) {
            publishDetailUpdate("task", message.taskId(), message.task());
        }
    }

    public void publishCalendarEvent(CalendarEventMessage message) {
        if (message.teamId() != null) {
            messagingTemplate.convertAndSend("/topic/calendar/" + message.teamId(), message);
        } else {
            messagingTemplate.convertAndSend("/topic/calendar", message);
        }
        if (message.event() != null && message.eventId() != null) {
            publishDetailUpdate("event", message.eventId(), message.event());
        }
    }

    public void publishScheduleProgress(ScheduleProgressMessage message) {
        if (message.teamId() != null) {
            messagingTemplate.convertAndSend("/topic/schedules/" + message.teamId(), message);
        } else {
            messagingTemplate.convertAndSend("/topic/schedules", message);
        }
    }

    public void publishScheduleBroadcast(ScheduleResponse schedule) {
        if (schedule == null || schedule.getTeamId() == null) {
            return;
        }
        messagingTemplate.convertAndSend("/topic/schedule." + schedule.getTeamId(), schedule);
    }

    public void publishConflictAlert(ConflictAlertMessage message) {
        if (message.teamId() != null) {
            messagingTemplate.convertAndSend("/topic/conflicts/" + message.teamId(), message);
        } else {
            messagingTemplate.convertAndSend("/topic/conflicts", message);
        }
    }

    public void publishNotification(CollaborationNotificationMessage message) {
        if ("USER".equalsIgnoreCase(message.scope()) && message.targetId() != null) {
            String userTopic = "/topic/notifications/user/" + message.targetId();
            System.out.println("[Notification] Publishing to user topic: " + userTopic + ", message: " + message.title());
            messagingTemplate.convertAndSend(userTopic, message);
        }
        if (message.teamId() != null) {
            String teamTopic = "/topic/notifications/team/" + message.teamId();
            System.out.println("[Notification] Publishing to team topic: " + teamTopic + ", message: " + message.title());
            messagingTemplate.convertAndSend(teamTopic, message);
        } else if (!StringUtils.hasText(message.scope()) || "BROADCAST".equalsIgnoreCase(message.scope())) {
            System.out.println("[Notification] Publishing to broadcast topic: /topic/notifications, message: " + message.title());
            messagingTemplate.convertAndSend("/topic/notifications", message);
        }
    }

    public void publishDetailUpdate(String entityType, Long entityId, Object payload) {
        if (entityType == null || entityId == null) {
            return;
        }
        messagingTemplate.convertAndSend("/topic/detail/" + entityType + "/" + entityId, payload);
    }
}

