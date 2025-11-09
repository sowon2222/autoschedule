package com.example.sbb.controller;

import com.example.sbb.dto.request.TeamCreateRequest;
import com.example.sbb.dto.request.TeamUpdateRequest;
import com.example.sbb.dto.request.TeamInviteRequest;
import com.example.sbb.dto.response.TeamResponse;
import com.example.sbb.dto.response.TeamMemberResponse;
import com.example.sbb.service.TeamService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "Team API", description = "팀 및 팀 구성원 CRUD API")
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
    @Operation(summary = "팀 생성", description = "새로운 팀을 생성합니다.")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "생성 성공",
            content = @Content(schema = @Schema(implementation = TeamResponse.class))),
        @ApiResponse(responseCode = "400", description = "요청 검증 실패")
    })
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
    @Operation(summary = "팀 단건 조회", description = "팀 ID로 단일 팀 정보를 조회합니다.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "조회 성공",
            content = @Content(schema = @Schema(implementation = TeamResponse.class))),
        @ApiResponse(responseCode = "404", description = "팀을 찾을 수 없음")
    })
    public ResponseEntity<TeamResponse> getTeam(@Parameter(description = "팀 ID", example = "1") @PathVariable Long id) {
        return ResponseEntity.ok(teamService.findById(id));
    }

    /**
     * 모든 팀을 조회합니다.
     *
     * @return 모든 팀 목록 (200 OK)
     */
    @GetMapping
    @Operation(summary = "팀 목록 조회", description = "모든 팀 목록을 조회합니다.")
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
    @Operation(summary = "팀 수정", description = "팀 ID로 팀 정보를 수정합니다.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "수정 성공",
            content = @Content(schema = @Schema(implementation = TeamResponse.class))),
        @ApiResponse(responseCode = "404", description = "팀을 찾을 수 없음")
    })
    public ResponseEntity<TeamResponse> updateTeam(
        @Parameter(description = "팀 ID", example = "1") @PathVariable Long id,
        @RequestBody TeamUpdateRequest request) {
        return ResponseEntity.ok(teamService.updateTeam(id, request));
    }

    /**
     * 팀을 삭제합니다.
     *
     * @param id 팀 ID
     * @return 204 No Content
     */
    @DeleteMapping("/{id}")
    @Operation(summary = "팀 삭제", description = "팀 ID로 팀을 삭제합니다.")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "삭제 성공"),
        @ApiResponse(responseCode = "404", description = "팀을 찾을 수 없음")
    })
    public ResponseEntity<Void> deleteTeam(@Parameter(description = "팀 ID", example = "1") @PathVariable Long id) {
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
    @Operation(summary = "팀 구성원 초대", description = "팀 ID와 초대 정보를 입력하여 팀 구성원을 추가합니다.")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "초대 성공",
            content = @Content(schema = @Schema(implementation = TeamMemberResponse.class))),
        @ApiResponse(responseCode = "400", description = "요청 검증 실패"),
        @ApiResponse(responseCode = "404", description = "팀 또는 사용자를 찾을 수 없음")
    })
    public ResponseEntity<TeamMemberResponse> inviteMember(
        @Parameter(description = "팀 ID", example = "1") @PathVariable Long id,
        @Valid @RequestBody TeamInviteRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(teamService.inviteMember(id, request));
    }

    /**
     * 팀의 모든 구성원을 조회합니다.
     *
     * @param id 팀 ID
     * @return 팀 구성원 목록 (200 OK)
     */
    @GetMapping("/{id}/members")
    @Operation(summary = "팀 구성원 목록", description = "팀 ID 기준으로 모든 팀 구성원을 조회합니다.")
    public ResponseEntity<List<TeamMemberResponse>> getMembers(@Parameter(description = "팀 ID", example = "1") @PathVariable Long id) {
        return ResponseEntity.ok(teamService.listMembers(id));
    }

    /**
     * 사용자가 속한 모든 팀을 조회합니다.
     *
     * @param userId 사용자 ID
     * @return 사용자가 속한 팀 목록 (200 OK)
     */
    @GetMapping("/user/{userId}")
    @Operation(summary = "사용자별 팀 목록", description = "사용자 ID 기준으로 사용자가 속한 팀 목록을 조회합니다.")
    public ResponseEntity<List<TeamResponse>> getTeamsByUser(@Parameter(description = "사용자 ID", example = "5") @PathVariable Long userId) {
        return ResponseEntity.ok(teamService.findTeamsByUser(userId));
    }
}


