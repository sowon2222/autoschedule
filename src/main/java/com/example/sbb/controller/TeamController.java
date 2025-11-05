package com.example.sbb.controller;

import com.example.sbb.dto.request.TeamCreateRequest;
import com.example.sbb.dto.request.TeamUpdateRequest;
import com.example.sbb.dto.request.TeamInviteRequest;
import com.example.sbb.dto.response.TeamResponse;
import com.example.sbb.dto.response.TeamMemberResponse;
import com.example.sbb.service.TeamService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/teams")
public class TeamController {
    private final TeamService teamService;

    public TeamController(TeamService teamService) {
        this.teamService = teamService;
    }

    @PostMapping
    public ResponseEntity<TeamResponse> createTeam(@RequestBody TeamCreateRequest request) {
        TeamResponse response = teamService.createTeam(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<TeamResponse> getTeam(@PathVariable Long id) {
        return ResponseEntity.ok(teamService.findById(id));
    }

    @GetMapping
    public ResponseEntity<List<TeamResponse>> getAllTeams() {
        return ResponseEntity.ok(teamService.findAll());
    }

    @PutMapping("/{id}")
    public ResponseEntity<TeamResponse> updateTeam(@PathVariable Long id, @RequestBody TeamUpdateRequest request) {
        return ResponseEntity.ok(teamService.updateTeam(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteTeam(@PathVariable Long id) {
        teamService.deleteTeam(id);
        return ResponseEntity.noContent().build();
    }

    // --- members ---
    @PostMapping("/{id}/invite")
    public ResponseEntity<TeamMemberResponse> invite(@PathVariable Long id, @RequestBody TeamInviteRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(teamService.inviteMember(id, request));
    }

    @GetMapping("/{id}/members")
    public ResponseEntity<List<TeamMemberResponse>> members(@PathVariable Long id) {
        return ResponseEntity.ok(teamService.listMembers(id));
    }

    @GetMapping("/user/{userId}")
    public ResponseEntity<List<TeamResponse>> teamsOfUser(@PathVariable Long userId) {
        return ResponseEntity.ok(teamService.findTeamsByUser(userId));
    }
}


