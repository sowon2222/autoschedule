package com.example.sbb.dto.event;

import com.example.sbb.dto.response.CalendarEventResponse;

public record CalendarEventMessage(Long teamId,
                                   String action,
                                   CalendarEventResponse event,
                                   Long eventId) {

    public static CalendarEventMessage created(CalendarEventResponse event) {
        Long id = event != null ? event.getId() : null;
        Long teamId = event != null ? event.getTeamId() : null;
        return new CalendarEventMessage(teamId, "CREATED", event, id);
    }

    public static CalendarEventMessage updated(CalendarEventResponse event) {
        Long id = event != null ? event.getId() : null;
        Long teamId = event != null ? event.getTeamId() : null;
        return new CalendarEventMessage(teamId, "UPDATED", event, id);
    }

    public static CalendarEventMessage deleted(Long teamId, Long eventId) {
        return new CalendarEventMessage(teamId, "DELETED", null, eventId);
    }
}

