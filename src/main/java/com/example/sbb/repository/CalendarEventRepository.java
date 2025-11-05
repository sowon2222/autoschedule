package com.example.sbb.repository;

import com.example.sbb.domain.CalendarEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.OffsetDateTime;
import java.util.List;

public interface CalendarEventRepository extends JpaRepository<CalendarEvent, Long> {
    List<CalendarEvent> findByTeam_Id(Long teamId);
    List<CalendarEvent> findByTeam_IdAndStartsAtBetween(Long teamId, OffsetDateTime start, OffsetDateTime end);
    List<CalendarEvent> findByOwner_Id(Long ownerId);
    List<CalendarEvent> findByOwner_IdAndStartsAtBetween(Long ownerId, OffsetDateTime start, OffsetDateTime end);
}


