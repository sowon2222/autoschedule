package com.example.sbb.dto.event;

import com.example.sbb.dto.response.TaskResponse;

public record TaskEventMessage(String action, TaskResponse task, Long taskId, Long teamId) {

    public static TaskEventMessage created(TaskResponse task) {
        Long id = task != null ? task.getId() : null;
        Long teamId = task != null ? task.getTeamId() : null;
        return new TaskEventMessage("CREATED", task, id, teamId);
    }

    public static TaskEventMessage updated(TaskResponse task) {
        Long id = task != null ? task.getId() : null;
        Long teamId = task != null ? task.getTeamId() : null;
        return new TaskEventMessage("UPDATED", task, id, teamId);
    }

    public static TaskEventMessage deleted(Long taskId, Long teamId) {
        return new TaskEventMessage("DELETED", null, taskId, teamId);
    }
}

