package com.example.sbb.dto.event;

import com.example.sbb.dto.response.CalendarEventResponse;

import java.util.List;

public record ConflictAlertMessage(Long teamId,
                                   String sourceType,
                                   Long sourceId,
                                   CalendarEventResponse source,
                                   List<CalendarEventResponse> conflicts,
                                   String message) {

    public static ConflictAlertMessage calendarConflict(Long teamId,
                                                        CalendarEventResponse source,
                                                        List<CalendarEventResponse> conflicts,
                                                        String message) {
        Long sourceId = source != null ? source.getId() : null;
        return new ConflictAlertMessage(teamId, "CALENDAR_EVENT", sourceId, source, conflicts, message);
    }
}

