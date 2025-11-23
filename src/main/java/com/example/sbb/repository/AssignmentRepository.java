package com.example.sbb.repository;

import com.example.sbb.domain.Assignment;
import com.example.sbb.dto.response.AssignmentResponse;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AssignmentRepository extends JpaRepository<Assignment, Long> {
    
    @Query("""
        SELECT new com.example.sbb.dto.response.AssignmentResponse(
            a.id,
            a.schedule.id,
            a.task.id,
            a.task.title,
            a.title,
            a.startsAt,
            a.endsAt,
            a.source,
            a.slotIndex,
            a.meta
        )
        FROM Assignment a
        LEFT JOIN a.task
        WHERE a.schedule.id = :scheduleId
    """)
    List<AssignmentResponse> findByScheduleId(@Param("scheduleId") Long scheduleId);
    
    @Query("""
        SELECT new com.example.sbb.dto.response.AssignmentResponse(
            a.id,
            a.schedule.id,
            a.task.id,
            a.task.title,
            a.title,
            a.startsAt,
            a.endsAt,
            a.source,
            a.slotIndex,
            a.meta
        )
        FROM Assignment a
        LEFT JOIN a.task
        WHERE a.task.id = :taskId
    """)
    List<AssignmentResponse> findByTaskId(@Param("taskId") Long taskId);
    
    @Query("""
        SELECT new com.example.sbb.dto.response.AssignmentResponse(
            a.id,
            a.schedule.id,
            a.task.id,
            a.task.title,
            a.title,
            a.startsAt,
            a.endsAt,
            a.source,
            a.slotIndex,
            a.meta
        )
        FROM Assignment a
        LEFT JOIN a.task
        LEFT JOIN a.schedule s
        WHERE s.team.id = :teamId
        AND a.startsAt >= :start
        AND a.endsAt <= :end
    """)
    List<AssignmentResponse> findByTeamIdAndDateRange(@Param("teamId") Long teamId,
                                                       @Param("start") java.time.OffsetDateTime start,
                                                       @Param("end") java.time.OffsetDateTime end);
    
    @Modifying
    @Query("""
        DELETE FROM Assignment a
        WHERE a.schedule.team.id = :teamId
    """)
    void deleteByTeamId(@Param("teamId") Long teamId);
}

