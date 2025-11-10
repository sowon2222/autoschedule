package com.example.sbb.service;

import com.example.sbb.domain.CalendarEvent;
import com.example.sbb.domain.Team;
import com.example.sbb.domain.User;
import com.example.sbb.dto.event.CalendarEventMessage;
import com.example.sbb.dto.event.CollaborationNotificationMessage;
import com.example.sbb.dto.event.ConflictAlertMessage;
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
import java.util.Objects;
import java.util.stream.Collectors;

@Service
public class CalendarEventService {
    private final CalendarEventRepository calendarEventRepository;
    private final TeamRepository teamRepository;
    private final UserRepository userRepository;
    private final CollaborationEventPublisher eventPublisher;

    public CalendarEventService(CalendarEventRepository calendarEventRepository,
                                TeamRepository teamRepository,
                                UserRepository userRepository,
                                CollaborationEventPublisher eventPublisher) {
        this.calendarEventRepository = calendarEventRepository;
        this.teamRepository = teamRepository;
        this.userRepository = userRepository;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public CalendarEventResponse createEvent(CalendarEventCreateRequest request) {
        // 일정 시간 검증: 종료 시간은 시작 시간 이후여야 함
        if (request.getEndsAt() != null && request.getStartsAt() != null) {
            if (request.getEndsAt().isBefore(request.getStartsAt()) || request.getEndsAt().isEqual(request.getStartsAt())) {
                throw new IllegalArgumentException("종료 시간은 시작 시간 이후여야 합니다. 시작: " + request.getStartsAt() + ", 종료: " + request.getEndsAt());
            }
        } else if (request.getStartsAt() == null) {
            throw new IllegalArgumentException("시작 시간은 필수입니다");
        } else if (request.getEndsAt() == null) {
            throw new IllegalArgumentException("종료 시간은 필수입니다");
        }
        
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
        CalendarEventResponse response = toResponse(saved);
        eventPublisher.publishCalendarEvent(CalendarEventMessage.created(response));
        publishCalendarNotification(saved, "CALENDAR_CREATED", "새 일정 생성", "일정 '" + saved.getTitle() + "' 이(가) 생성되었습니다.");
        publishConflicts(saved);
        return response;
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
        OffsetDateTime originalStartsAt = e.getStartsAt();
        OffsetDateTime originalEndsAt = e.getEndsAt();

        // 일정 시간 검증: 종료 시간은 시작 시간 이후여야 함
        OffsetDateTime startsAt = request.getStartsAt() != null ? request.getStartsAt() : e.getStartsAt();
        OffsetDateTime endsAt = request.getEndsAt() != null ? request.getEndsAt() : e.getEndsAt();
        
        if (startsAt != null && endsAt != null) {
            if (endsAt.isBefore(startsAt) || endsAt.isEqual(startsAt)) {
                throw new IllegalArgumentException("종료 시간은 시작 시간 이후여야 합니다. 시작: " + startsAt + ", 종료: " + endsAt);
            }
        }
        
        if (request.getTitle() != null) e.setTitle(request.getTitle());
        if (request.getLocation() != null) e.setLocation(request.getLocation());
        if (request.getStartsAt() != null) e.setStartsAt(request.getStartsAt());
        if (request.getEndsAt() != null) e.setEndsAt(request.getEndsAt());
        if (request.getFixed() != null) e.setFixed(request.getFixed());
        if (request.getAttendees() != null) e.setAttendees(request.getAttendees());
        if (request.getNotes() != null) e.setNotes(request.getNotes());
        e.setUpdatedAt(OffsetDateTime.now());
        CalendarEvent saved = calendarEventRepository.save(e);
        CalendarEventResponse response = toResponse(saved);
        eventPublisher.publishCalendarEvent(CalendarEventMessage.updated(response));
        publishUpdateNotifications(e, request, originalStartsAt, originalEndsAt);
        publishConflicts(saved);
        return response;
    }

    @Transactional
    public void deleteEvent(Long id) {
        CalendarEvent event = calendarEventRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("이벤트를 찾을 수 없습니다: " + id));
        calendarEventRepository.delete(event);
        Long teamId = event.getTeam() != null ? event.getTeam().getId() : null;
        eventPublisher.publishCalendarEvent(CalendarEventMessage.deleted(teamId, id));
        publishCalendarNotification(event, "CALENDAR_DELETED", "일정 삭제", "일정 '" + event.getTitle() + "' 이(가) 삭제되었습니다.");
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

    private void publishConflicts(CalendarEvent event) {
        if (event.getTeam() == null) {
            return;
        }
        List<CalendarEventResponse> conflicts = calendarEventRepository.findByTeam_Id(event.getTeam().getId()).stream()
            .filter(existing -> !Objects.equals(existing.getId(), event.getId()))
            .filter(existing -> isOverlapping(existing, event))
            .map(this::toResponse)
            .collect(Collectors.toList());
        if (!conflicts.isEmpty()) {
            CalendarEventResponse source = toResponse(event);
            String message = "일정 '" + event.getTitle() + "' 이(가) 다른 일정과 충돌합니다.";
            ConflictAlertMessage conflictAlert = ConflictAlertMessage.calendarConflict(
                event.getTeam().getId(),
                source,
                conflicts,
                message
            );
            eventPublisher.publishConflictAlert(conflictAlert);
        }
    }

    private boolean isOverlapping(CalendarEvent existing, CalendarEvent target) {
        return existing.getStartsAt().isBefore(target.getEndsAt()) && existing.getEndsAt().isAfter(target.getStartsAt());
    }

    private void publishCalendarNotification(CalendarEvent event, String category, String title, String content) {
        if (event.getTeam() != null) {
            eventPublisher.publishNotification(
                CollaborationNotificationMessage.team(
                    event.getTeam().getId(),
                    category,
                    title,
                    content
                )
            );
        }
    }

    private void publishUpdateNotifications(CalendarEvent event,
                                            CalendarEventUpdateRequest request,
                                            OffsetDateTime originalStartsAt,
                                            OffsetDateTime originalEndsAt) {
        publishCalendarNotification(event,
            "CALENDAR_UPDATED",
            "일정 수정",
            "일정 '" + event.getTitle() + "' 정보가 업데이트되었습니다.");

        boolean timeChanged = (request.getStartsAt() != null && !request.getStartsAt().equals(originalStartsAt))
            || (request.getEndsAt() != null && !request.getEndsAt().equals(originalEndsAt));

        if (timeChanged) {
            publishCalendarNotification(event,
                "CALENDAR_TIME_CHANGED",
                "일정 시간 변경",
                "일정 '" + event.getTitle() + "' 의 시간이 변경되었습니다.");
        }
    }
}


