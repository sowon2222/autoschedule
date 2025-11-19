package com.example.sbb.repository;

import com.example.sbb.domain.Task;
import com.example.sbb.dto.response.TaskResponse;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface TaskRepository extends JpaRepository<Task, Long> {
    // DTO 프로젝션: 엔티티를 거치지 않고 바로 DTO로 조회 (Lazy loading 문제 원천 제거)
    @Query("""
        SELECT new com.example.sbb.dto.response.TaskResponse(
            t.id,
            t.team.id,
            t.team.name,
            t.assignee.id,
            t.assignee.name,
            t.title,
            t.durationMin,
            t.dueAt,
            t.priority,
            t.splittable,
            t.tags,
            t.createdAt,
            t.updatedAt
        )
        FROM Task t
        LEFT JOIN t.team
        LEFT JOIN t.assignee
        WHERE t.id = :id
    """)
    Optional<TaskResponse> findResponseById(@Param("id") Long id);
    
    @Query("""
        SELECT new com.example.sbb.dto.response.TaskResponse(
            t.id,
            t.team.id,
            t.team.name,
            t.assignee.id,
            t.assignee.name,
            t.title,
            t.durationMin,
            t.dueAt,
            t.priority,
            t.splittable,
            t.tags,
            t.createdAt,
            t.updatedAt
        )
        FROM Task t
        LEFT JOIN t.team
        LEFT JOIN t.assignee
        WHERE t.team.id = :teamId
    """)
    List<TaskResponse> findResponsesByTeamId(@Param("teamId") Long teamId);
    
    @Query("""
        SELECT new com.example.sbb.dto.response.TaskResponse(
            t.id,
            t.team.id,
            t.team.name,
            t.assignee.id,
            t.assignee.name,
            t.title,
            t.durationMin,
            t.dueAt,
            t.priority,
            t.splittable,
            t.tags,
            t.createdAt,
            t.updatedAt
        )
        FROM Task t
        LEFT JOIN t.team
        LEFT JOIN t.assignee
        WHERE t.assignee.id = :assigneeId
    """)
    List<TaskResponse> findResponsesByAssigneeId(@Param("assigneeId") Long assigneeId);
}


