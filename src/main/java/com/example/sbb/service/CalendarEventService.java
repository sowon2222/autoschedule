package com.example.sbb.service;

import com.example.sbb.domain.CalendarEvent;
import com.example.sbb.domain.Team;
import com.example.sbb.domain.User;
import com.example.sbb.dto.request.CalendarEventCreateRequest;
import com.example.sbb.dto.request.CalendarEventUpdateRequest;
import com.example.sbb.dto.response.CalendarEventResponse;
import com.example.sbb.repository.CalendarEventRepository;
import com.example.sbb.repository.TeamRepository;
import com.example.sbb.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class CalendarEventService {
    private final CalendarEventRepository calendarEventRepository;
    private final TeamRepository teamRepository;
    private final UserRepository userRepository;

    public CalendarEventService(CalendarEventRepository calendarEventRepository, TeamRepository teamRepository, UserRepository userRepository) {
        this.calendarEventRepository = calendarEventRepository;
        this.teamRepository = teamRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public CalendarEventResponse createEvent(CalendarEventCreateRequest request) {
        CalendarEvent e = new CalendarEvent();
        Team team = teamRepository.findById(request.getTeamId())
            .orElseThrow(() -> new IllegalArgumentException("팀을 찾을 수 없습니다: " + request.getTeamId()));
        e.setTeam(team);
        if (request.getOwnerId() != null) {
            User owner = userRepository.findById(request.getOwnerId())
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다: " + request.getOwnerId()));
            e.setOwner(owner);
        }
        e.setTitle(request.getTitle());
        e.setLocation(request.getLocation());
        e.setStartsAt(request.getStartsAt());
        e.setEndsAt(request.getEndsAt());
        e.setFixed(request.getFixed() != null && request.getFixed());
        e.setAttendees(request.getAttendees());
        e.setNotes(request.getNotes());
        e.setCreatedAt(OffsetDateTime.now());
        e.setUpdatedAt(OffsetDateTime.now());
        CalendarEvent saved = calendarEventRepository.save(e);
        return toResponse(saved);
    }

    public CalendarEventResponse findById(Long id) {
        CalendarEvent e = calendarEventRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("이벤트를 찾을 수 없습니다: " + id));
        return toResponse(e);
    }

    public List<CalendarEventResponse> findByTeam(Long teamId) {
        return calendarEventRepository.findByTeam_Id(teamId).stream().map(this::toResponse).collect(Collectors.toList());
    }

    public List<CalendarEventResponse> findByTeamAndRange(Long teamId, java.time.OffsetDateTime start, java.time.OffsetDateTime end) {
        return calendarEventRepository.findByTeam_IdAndStartsAtBetween(teamId, start, end).stream().map(this::toResponse).collect(Collectors.toList());
    }

    public List<CalendarEventResponse> findByOwner(Long ownerId) {
        return calendarEventRepository.findByOwner_Id(ownerId).stream().map(this::toResponse).collect(Collectors.toList());
    }

    public List<CalendarEventResponse> findByOwnerAndRange(Long ownerId, java.time.OffsetDateTime start, java.time.OffsetDateTime end) {
        return calendarEventRepository.findByOwner_IdAndStartsAtBetween(ownerId, start, end).stream().map(this::toResponse).collect(Collectors.toList());
    }

    @Transactional
    public CalendarEventResponse updateEvent(Long id, CalendarEventUpdateRequest request) {
        CalendarEvent e = calendarEventRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("이벤트를 찾을 수 없습니다: " + id));
        if (request.getTitle() != null) e.setTitle(request.getTitle());
        if (request.getLocation() != null) e.setLocation(request.getLocation());
        if (request.getStartsAt() != null) e.setStartsAt(request.getStartsAt());
        if (request.getEndsAt() != null) e.setEndsAt(request.getEndsAt());
        if (request.getFixed() != null) e.setFixed(request.getFixed());
        if (request.getAttendees() != null) e.setAttendees(request.getAttendees());
        if (request.getNotes() != null) e.setNotes(request.getNotes());
        e.setUpdatedAt(OffsetDateTime.now());
        CalendarEvent saved = calendarEventRepository.save(e);
        return toResponse(saved);
    }

    @Transactional
    public void deleteEvent(Long id) {
        if (!calendarEventRepository.existsById(id)) throw new IllegalArgumentException("이벤트를 찾을 수 없습니다: " + id);
        calendarEventRepository.deleteById(id);
    }

    private CalendarEventResponse toResponse(CalendarEvent e) {
        CalendarEventResponse r = new CalendarEventResponse();
        r.setId(e.getId());
        r.setTeamId(e.getTeam() != null ? e.getTeam().getId() : null);
        r.setTeamName(e.getTeam() != null ? e.getTeam().getName() : null);
        r.setOwnerId(e.getOwner() != null ? e.getOwner().getId() : null);
        r.setOwnerName(e.getOwner() != null ? e.getOwner().getName() : null);
        r.setTitle(e.getTitle());
        r.setLocation(e.getLocation());
        r.setStartsAt(e.getStartsAt());
        r.setEndsAt(e.getEndsAt());
        r.setFixed(e.isFixed());
        r.setAttendees(e.getAttendees());
        r.setNotes(e.getNotes());
        r.setCreatedAt(e.getCreatedAt());
        r.setUpdatedAt(e.getUpdatedAt());
        return r;
    }
}


