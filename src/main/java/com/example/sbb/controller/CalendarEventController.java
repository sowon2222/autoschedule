package com.example.sbb.controller;

import com.example.sbb.dto.request.CalendarEventCreateRequest;
import com.example.sbb.dto.request.CalendarEventUpdateRequest;
import com.example.sbb.dto.response.CalendarEventResponse;
import com.example.sbb.service.CalendarEventService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/events")
public class CalendarEventController {
    private final CalendarEventService calendarEventService;

    public CalendarEventController(CalendarEventService calendarEventService) {
        this.calendarEventService = calendarEventService;
    }

    @PostMapping
    public ResponseEntity<CalendarEventResponse> create(@RequestBody CalendarEventCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(calendarEventService.createEvent(request));
    }

    @GetMapping("/{id}")
    public ResponseEntity<CalendarEventResponse> get(@PathVariable Long id) {
        return ResponseEntity.ok(calendarEventService.findById(id));
    }

    @GetMapping("/team/{teamId}")
    public ResponseEntity<List<CalendarEventResponse>> byTeam(@PathVariable Long teamId) {
        return ResponseEntity.ok(calendarEventService.findByTeam(teamId));
    }

    @GetMapping("/team/{teamId}/range")
    public ResponseEntity<List<CalendarEventResponse>> byRange(
        @PathVariable Long teamId,
        @RequestParam java.time.OffsetDateTime start,
        @RequestParam java.time.OffsetDateTime end
    ) {
        return ResponseEntity.ok(calendarEventService.findByTeamAndRange(teamId, start, end));
    }

    @GetMapping("/user/{userId}")
    public ResponseEntity<List<CalendarEventResponse>> byUser(@PathVariable Long userId) {
        return ResponseEntity.ok(calendarEventService.findByOwner(userId));
    }

    @GetMapping("/user/{userId}/range")
    public ResponseEntity<List<CalendarEventResponse>> byUserAndRange(
        @PathVariable Long userId,
        @RequestParam java.time.OffsetDateTime start,
        @RequestParam java.time.OffsetDateTime end
    ) {
        return ResponseEntity.ok(calendarEventService.findByOwnerAndRange(userId, start, end));
    }

    @PutMapping("/{id}")
    public ResponseEntity<CalendarEventResponse> update(@PathVariable Long id, @RequestBody CalendarEventUpdateRequest request) {
        return ResponseEntity.ok(calendarEventService.updateEvent(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        calendarEventService.deleteEvent(id);
        return ResponseEntity.noContent().build();
    }
}


