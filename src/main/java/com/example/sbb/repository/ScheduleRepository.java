package com.example.sbb.repository;

import com.example.sbb.domain.Schedule;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ScheduleRepository extends JpaRepository<Schedule, Long> {
    
    @Query("""
        SELECT s FROM Schedule s
        WHERE s.team.id = :teamId
        AND s.rangeStart <= :date
        AND s.rangeEnd >= :date
        ORDER BY s.createdAt DESC
    """)
    Optional<Schedule> findLatestByTeamIdAndDate(@Param("teamId") Long teamId, @Param("date") LocalDate date);
    
    @Query("""
        SELECT s FROM Schedule s
        WHERE s.team.id = :teamId
        AND s.rangeStart <= :rangeEnd
        AND s.rangeEnd >= :rangeStart
        ORDER BY s.createdAt DESC
    """)
    List<Schedule> findByTeamIdAndDateRange(@Param("teamId") Long teamId, 
                                             @Param("rangeStart") LocalDate rangeStart,
                                             @Param("rangeEnd") LocalDate rangeEnd);
    
    @Query("""
        SELECT s FROM Schedule s
        WHERE s.team.id = :teamId
        ORDER BY s.createdAt DESC
    """)
    List<Schedule> findByTeamId(@Param("teamId") Long teamId);
}

