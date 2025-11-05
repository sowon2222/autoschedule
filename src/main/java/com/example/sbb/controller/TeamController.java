package com.example.sbb.controller;

import com.example.sbb.dto.request.TeamCreateRequest;
import com.example.sbb.dto.request.TeamUpdateRequest;
import com.example.sbb.dto.request.TeamInviteRequest;
import com.example.sbb.dto.response.TeamResponse;
import com.example.sbb.dto.response.TeamMemberResponse;
import com.example.sbb.service.TeamService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 팀 및 팀 구성원 관리 REST API 컨트롤러
 */
@RestController
@RequestMapping("/api/teams")
public class TeamController {
    private final TeamService teamService;

    public TeamController(TeamService teamService) {
        this.teamService = teamService;
    }

    // ========== 팀 관리 API ==========

    /**
     * 새 팀을 생성합니다.
     *
     * @param request 팀 생성 요청
     * @return 생성된 팀 정보 (201 Created)
     */
    @PostMapping
    public ResponseEntity<TeamResponse> createTeam(@Valid @RequestBody TeamCreateRequest request) {
        TeamResponse response = teamService.createTeam(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * ID로 팀을 조회합니다.
     *
     * @param id 팀 ID
     * @return 팀 정보 (200 OK)
     */
    @GetMapping("/{id}")
    public ResponseEntity<TeamResponse> getTeam(@PathVariable Long id) {
        return ResponseEntity.ok(teamService.findById(id));
    }

    /**
     * 모든 팀을 조회합니다.
     *
     * @return 모든 팀 목록 (200 OK)
     */
    @GetMapping
    public ResponseEntity<List<TeamResponse>> getAllTeams() {
        return ResponseEntity.ok(teamService.findAll());
    }

    /**
     * 팀 정보를 업데이트합니다.
     *
     * @param id 팀 ID
     * @param request 팀 업데이트 요청
     * @return 업데이트된 팀 정보 (200 OK)
     */
    @PutMapping("/{id}")
    public ResponseEntity<TeamResponse> updateTeam(@PathVariable Long id, @RequestBody TeamUpdateRequest request) {
        return ResponseEntity.ok(teamService.updateTeam(id, request));
    }

    /**
     * 팀을 삭제합니다.
     *
     * @param id 팀 ID
     * @return 204 No Content
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteTeam(@PathVariable Long id) {
        teamService.deleteTeam(id);
        return ResponseEntity.noContent().build();
    }

    // ========== 팀 구성원 관리 API ==========

    /**
     * 팀에 구성원을 초대합니다.
     *
     * @param id 팀 ID
     * @param request 초대 요청 (userId 또는 email, role)
     * @return 추가된 팀 구성원 정보 (201 Created)
     */
    @PostMapping("/{id}/invite")
    public ResponseEntity<TeamMemberResponse> inviteMember(@PathVariable Long id, @Valid @RequestBody TeamInviteRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(teamService.inviteMember(id, request));
    }

    /**
     * 팀의 모든 구성원을 조회합니다.
     *
     * @param id 팀 ID
     * @return 팀 구성원 목록 (200 OK)
     */
    @GetMapping("/{id}/members")
    public ResponseEntity<List<TeamMemberResponse>> getMembers(@PathVariable Long id) {
        return ResponseEntity.ok(teamService.listMembers(id));
    }

    /**
     * 사용자가 속한 모든 팀을 조회합니다.
     *
     * @param userId 사용자 ID
     * @return 사용자가 속한 팀 목록 (200 OK)
     */
    @GetMapping("/user/{userId}")
    public ResponseEntity<List<TeamResponse>> getTeamsByUser(@PathVariable Long userId) {
        return ResponseEntity.ok(teamService.findTeamsByUser(userId));
    }
}


