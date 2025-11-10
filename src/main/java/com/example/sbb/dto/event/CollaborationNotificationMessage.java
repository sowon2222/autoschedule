package com.example.sbb.dto.event;

import java.time.OffsetDateTime;

public record CollaborationNotificationMessage(Long teamId,
                                               String scope,
                                               Long targetId,
                                               String category,
                                               String title,
                                               String content,
                                               OffsetDateTime timestamp) {

    public static CollaborationNotificationMessage team(Long teamId,
                                                        String category,
                                                        String title,
                                                        String content) {
        return new CollaborationNotificationMessage(teamId, "TEAM", teamId, category, title, content, OffsetDateTime.now());
    }

    public static CollaborationNotificationMessage user(Long teamId,
                                                        Long userId,
                                                        String category,
                                                        String title,
                                                        String content) {
        return new CollaborationNotificationMessage(teamId, "USER", userId, category, title, content, OffsetDateTime.now());
    }
}

