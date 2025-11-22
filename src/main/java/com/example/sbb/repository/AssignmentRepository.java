package com.example.sbb.repository;

import com.example.sbb.domain.Assignment;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AssignmentRepository extends JpaRepository<Assignment, Long> {
    
    List<Assignment> findByScheduleId(Long scheduleId);
    
    @Query("""
        SELECT a FROM Assignment a
        WHERE a.schedule.team.id = :teamId
        AND a.startsAt >= :start
        AND a.endsAt <= :end
    """)
    List<Assignment> findByTeamIdAndDateRange(@Param("teamId") Long teamId,
                                               @Param("start") java.time.OffsetDateTime start,
                                               @Param("end") java.time.OffsetDateTime end);
}

