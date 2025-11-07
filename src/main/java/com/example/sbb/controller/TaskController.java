package com.example.sbb.controller;

import com.example.sbb.dto.request.TaskCreateRequest;
import com.example.sbb.dto.request.TaskUpdateRequest;
import com.example.sbb.dto.response.TaskResponse;
import com.example.sbb.service.TaskService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/tasks")
public class TaskController {
    private final TaskService taskService;

    public TaskController(TaskService taskService) { this.taskService = taskService; }

    @PostMapping
    public ResponseEntity<TaskResponse> createTask(@Valid @RequestBody TaskCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(taskService.createTask(request));
    }

    @GetMapping("/{id}")
    public ResponseEntity<TaskResponse> getTask(@PathVariable Long id) {
        return ResponseEntity.ok(taskService.findById(id));
    }

    @GetMapping("/team/{teamId}")
    public ResponseEntity<List<TaskResponse>> tasksByTeam(@PathVariable Long teamId) {
        return ResponseEntity.ok(taskService.findByTeamId(teamId));
    }

    @GetMapping("/assignee/{assigneeId}")
    public ResponseEntity<List<TaskResponse>> tasksByAssignee(@PathVariable Long assigneeId) {
        return ResponseEntity.ok(taskService.findByAssigneeId(assigneeId));
    }

    @PutMapping("/{id}")
    public ResponseEntity<TaskResponse> updateTask(@PathVariable Long id, @Valid @RequestBody TaskUpdateRequest request) {
        return ResponseEntity.ok(taskService.updateTask(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteTask(@PathVariable Long id) {
        taskService.deleteTask(id);
        return ResponseEntity.noContent().build();
    }
}


