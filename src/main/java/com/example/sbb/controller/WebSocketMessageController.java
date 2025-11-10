package com.example.sbb.controller;

import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.stereotype.Controller;

@Controller
public class WebSocketMessageController {

    @MessageMapping("/tasks/update")
    @SendTo("/topic/tasks")
    public TaskUpdateMessage broadcastTaskUpdate(TaskUpdateMessage message) {
        return message;
    }

    public record TaskUpdateMessage(Long taskId, String status, String assignee) {
    }
}

