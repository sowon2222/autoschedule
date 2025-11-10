package com.example.sbb.dto.event;

import com.example.sbb.dto.response.ScheduleResponse;

public record ScheduleProgressMessage(Long teamId,
                                      String status,
                                      Integer progress,
                                      String message,
                                      ScheduleResponse schedule) {

    public static ScheduleProgressMessage progress(Long teamId, int progress, String message) {
        return new ScheduleProgressMessage(teamId, "PROGRESS", progress, message, null);
    }

    public static ScheduleProgressMessage completed(Long teamId, ScheduleResponse schedule) {
        return new ScheduleProgressMessage(teamId, "COMPLETED", 100, "Optimization completed", schedule);
    }

    public static ScheduleProgressMessage failed(Long teamId, String message) {
        return new ScheduleProgressMessage(teamId, "FAILED", null, message, null);
    }
}

