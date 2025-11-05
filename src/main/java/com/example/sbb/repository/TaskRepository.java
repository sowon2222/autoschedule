package com.example.sbb.repository;

import com.example.sbb.domain.Task;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TaskRepository extends JpaRepository<Task, Long> {
    List<Task> findByTeam_Id(Long teamId);
    List<Task> findByAssignee_Id(Long assigneeId);
}


