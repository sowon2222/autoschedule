package com.example.sbb.repository;

import com.example.sbb.domain.CalendarEvent;
import com.example.sbb.dto.response.CalendarEventResponse;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

public interface CalendarEventRepository extends JpaRepository<CalendarEvent, Long> {
    // DTO 프로젝션: 엔티티를 거치지 않고 바로 DTO로 조회 (Lazy loading 문제 원천 제거)
    @Query("""
        SELECT new com.example.sbb.dto.response.CalendarEventResponse(
            e.id,
            e.team.id,
            e.team.name,
            e.owner.id,
            e.owner.name,
            e.title,
            e.startsAt,
            e.endsAt,
            e.fixed,
            e.location,
            e.attendees,
            e.notes,
            e.recurrenceType,
            e.recurrenceEndDate,
            e.createdAt,
            e.updatedAt
        )
        FROM CalendarEvent e
        LEFT JOIN e.team
        LEFT JOIN e.owner
        WHERE e.id = :id
    """)
    Optional<CalendarEventResponse> findResponseById(@Param("id") Long id);
    
    @Query("""
        SELECT new com.example.sbb.dto.response.CalendarEventResponse(
            e.id,
            e.team.id,
            e.team.name,
            e.owner.id,
            e.owner.name,
            e.title,
            e.startsAt,
            e.endsAt,
            e.fixed,
            e.location,
            e.attendees,
            e.notes,
            e.recurrenceType,
            e.recurrenceEndDate,
            e.createdAt,
            e.updatedAt
        )
        FROM CalendarEvent e
        LEFT JOIN e.team
        LEFT JOIN e.owner
        WHERE e.team.id = :teamId
    """)
    List<CalendarEventResponse> findResponsesByTeamId(@Param("teamId") Long teamId);
    
    @Query("""
        SELECT new com.example.sbb.dto.response.CalendarEventResponse(
            e.id,
            e.team.id,
            e.team.name,
            e.owner.id,
            e.owner.name,
            e.title,
            e.startsAt,
            e.endsAt,
            e.fixed,
            e.location,
            e.attendees,
            e.notes,
            e.recurrenceType,
            e.recurrenceEndDate,
            e.createdAt,
            e.updatedAt
        )
        FROM CalendarEvent e
        LEFT JOIN e.team
        LEFT JOIN e.owner
        WHERE e.team.id = :teamId AND e.startsAt BETWEEN :start AND :end
    """)
    List<CalendarEventResponse> findResponsesByTeamIdAndRange(@Param("teamId") Long teamId, @Param("start") OffsetDateTime start, @Param("end") OffsetDateTime end);
    
    @Query("""
        SELECT new com.example.sbb.dto.response.CalendarEventResponse(
            e.id,
            e.team.id,
            e.team.name,
            e.owner.id,
            e.owner.name,
            e.title,
            e.startsAt,
            e.endsAt,
            e.fixed,
            e.location,
            e.attendees,
            e.notes,
            e.recurrenceType,
            e.recurrenceEndDate,
            e.createdAt,
            e.updatedAt
        )
        FROM CalendarEvent e
        LEFT JOIN e.team
        LEFT JOIN e.owner
        WHERE e.owner.id = :ownerId
    """)
    List<CalendarEventResponse> findResponsesByOwnerId(@Param("ownerId") Long ownerId);
    
    @Query("""
        SELECT new com.example.sbb.dto.response.CalendarEventResponse(
            e.id,
            e.team.id,
            e.team.name,
            e.owner.id,
            e.owner.name,
            e.title,
            e.startsAt,
            e.endsAt,
            e.fixed,
            e.location,
            e.attendees,
            e.notes,
            e.recurrenceType,
            e.recurrenceEndDate,
            e.createdAt,
            e.updatedAt
        )
        FROM CalendarEvent e
        LEFT JOIN e.team
        LEFT JOIN e.owner
        WHERE e.owner.id = :ownerId AND e.startsAt BETWEEN :start AND :end
    """)
    List<CalendarEventResponse> findResponsesByOwnerIdAndRange(@Param("ownerId") Long ownerId, @Param("start") OffsetDateTime start, @Param("end") OffsetDateTime end);
}


