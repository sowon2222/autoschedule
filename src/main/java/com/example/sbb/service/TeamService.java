package com.example.sbb.service;

import com.example.sbb.domain.Role;
import com.example.sbb.domain.Team;
import com.example.sbb.domain.TeamMember;
import com.example.sbb.domain.TeamMemberId;
import com.example.sbb.domain.User;
import com.example.sbb.dto.event.CollaborationNotificationMessage;
import com.example.sbb.dto.request.TeamCreateRequest;
import com.example.sbb.dto.request.TeamInviteRequest;
import com.example.sbb.dto.request.TeamUpdateRequest;
import com.example.sbb.dto.response.TeamMemberResponse;
import com.example.sbb.dto.response.TeamResponse;
import com.example.sbb.repository.TeamMemberRepository;
import com.example.sbb.repository.TeamRepository;
import com.example.sbb.repository.UserRepository;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * 팀 및 팀 구성원 관리 서비스
 */
@Service
public class TeamService {
    private final TeamRepository teamRepository;
    private final TeamMemberRepository teamMemberRepository;
    private final UserRepository userRepository;
    private final CollaborationEventPublisher eventPublisher;

    public TeamService(TeamRepository teamRepository,
                       TeamMemberRepository teamMemberRepository,
                       UserRepository userRepository,
                       CollaborationEventPublisher eventPublisher) {
        this.teamRepository = teamRepository;
        this.teamMemberRepository = teamMemberRepository;
        this.userRepository = userRepository;
        this.eventPublisher = eventPublisher;
    }

    // ========== 팀 관리 ==========

    /**
     * 새 팀을 생성합니다.
     *
     * @param request 팀 생성 요청 (이름 포함)
     * @return 생성된 팀 정보
     * @throws IllegalArgumentException 팀 이름이 비어있는 경우
     */
    @Transactional
    public TeamResponse createTeam(TeamCreateRequest request) {
        if (!StringUtils.hasText(request.getName())) {
            throw new IllegalArgumentException("팀 이름은 필수입니다.");
        }

        Team team = new Team();
        team.setName(request.getName().trim());
        team.setCreatedAt(OffsetDateTime.now());
        Team saved = teamRepository.save(team);
        TeamResponse response = toResponse(saved);
        eventPublisher.publishNotification(
            CollaborationNotificationMessage.team(
                saved.getId(),
                "TEAM_CREATED",
                "새 팀 생성",
                "팀 '" + saved.getName() + "' 이(가) 생성되었습니다.")
        );
        eventPublisher.publishDetailUpdate("team", saved.getId(), response);
        return response;
    }

    /**
     * ID로 팀을 조회합니다.
     *
     * @param id 팀 ID
     * @return 팀 정보
     * @throws IllegalArgumentException 팀을 찾을 수 없는 경우
     */
    @Transactional(readOnly = true)
    public TeamResponse findById(Long id) {
        Team team = teamRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("팀을 찾을 수 없습니다: " + id));
        return toResponse(team);
    }

    /**
     * 모든 팀을 조회합니다.
     *
     * @return 모든 팀 목록
     */
    @Transactional(readOnly = true)
    public List<TeamResponse> findAll() {
        return teamRepository.findAll().stream()
            .map(this::toResponse)
            .collect(Collectors.toList());
    }

    /**
     * 팀 정보를 업데이트합니다.
     *
     * @param id 팀 ID
     * @param request 업데이트 요청
     * @return 업데이트된 팀 정보
     * @throws IllegalArgumentException 팀을 찾을 수 없는 경우
     */
    @Transactional
    public TeamResponse updateTeam(Long id, TeamUpdateRequest request) {
        Team team = teamRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("팀을 찾을 수 없습니다: " + id));

        if (StringUtils.hasText(request.getName())) {
            team.setName(request.getName().trim());
        }

        Team saved = teamRepository.save(team);
        TeamResponse response = toResponse(saved);
        eventPublisher.publishNotification(
            CollaborationNotificationMessage.team(
                saved.getId(),
                "TEAM_UPDATED",
                "팀 정보 수정",
                "팀 '" + saved.getName() + "' 정보가 업데이트되었습니다.")
        );
        eventPublisher.publishDetailUpdate("team", saved.getId(), response);
        return response;
    }

    /**
     * 팀을 삭제합니다.
     * 팀 삭제 시 연관된 팀 구성원, 작업 시간, 일정 등도 함께 삭제됩니다 (Cascade 설정).
     *
     * @param id 팀 ID
     * @throws IllegalArgumentException 팀을 찾을 수 없는 경우
     */
    @Transactional
    public void deleteTeam(Long id) {
        Team team = teamRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("팀을 찾을 수 없습니다: " + id));
        teamRepository.delete(team);
        eventPublisher.publishNotification(
            CollaborationNotificationMessage.team(
                team.getId(),
                "TEAM_DELETED",
                "팀 삭제",
                "팀 '" + team.getName() + "' 이(가) 삭제되었습니다.")
        );
    }

    // ========== 팀 구성원 관리 ==========

    /**
     * 팀에 구성원을 초대합니다.
     * userId 또는 email 중 하나를 사용하여 사용자를 찾습니다.
     *
     * @param teamId 팀 ID
     * @param request 초대 요청 (userId 또는 email, role 포함)
     * @return 추가된 팀 구성원 정보
     * @throws IllegalArgumentException 팀/사용자를 찾을 수 없거나 필수 파라미터가 누락된 경우
     * @throws IllegalStateException 이미 팀에 속해 있는 경우
     */
    @Transactional
    public TeamMemberResponse inviteMember(Long teamId, TeamInviteRequest request) {
        Team team = teamRepository.findById(teamId)
            .orElseThrow(() -> new IllegalArgumentException("팀을 찾을 수 없습니다: " + teamId));

        User user = findUser(request);
        TeamMemberId id = new TeamMemberId(team.getId(), user.getId());

        if (teamMemberRepository.existsById(id)) {
            throw new IllegalStateException("이미 팀에 속해 있습니다.");
        }

        TeamMember member = new TeamMember();
        member.setId(id);
        member.setTeam(team);
        member.setUser(user);
        member.setRole(request.getRole() != null ? request.getRole() : Role.MEMBER);
        teamMemberRepository.save(member);

        CollaborationNotificationMessage messageForTeam = CollaborationNotificationMessage.team(
            team.getId(),
            "TEAM_MEMBER_ADDED",
            "팀 구성원 추가",
            "사용자 '" + user.getName() + "' 이(가) 팀에 합류했습니다."
        );
        eventPublisher.publishNotification(messageForTeam);

        CollaborationNotificationMessage messageForUser = CollaborationNotificationMessage.user(
            team.getId(),
            user.getId(),
            "TEAM_INVITE_ACCEPTED",
            "팀 참여 확정",
            "팀 '" + team.getName() + "' 에 참여가 완료되었습니다."
        );
        eventPublisher.publishNotification(messageForUser);

        TeamMemberResponse response = toMemberResponse(member);
        eventPublisher.publishDetailUpdate("teamMember", team.getId(), response);

        return response;
    }

    /**
     * 팀의 모든 구성원을 조회합니다.
     *
     * @param teamId 팀 ID
     * @return 팀 구성원 목록
     */
    @Transactional(readOnly = true)
    public List<TeamMemberResponse> listMembers(Long teamId) {
        return teamMemberRepository.findResponsesByTeamId(teamId);
    }

    /**
     * 사용자가 속한 모든 팀을 조회합니다.
     *
     * @param userId 사용자 ID
     * @return 사용자가 속한 팀 목록
     */
    @Transactional(readOnly = true)
    public List<TeamResponse> findTeamsByUser(Long userId) {
        return teamMemberRepository.findTeamResponsesByUserId(userId);
    }

    // ========== 내부 메서드 ==========

    /**
     * TeamInviteRequest에서 사용자를 찾습니다.
     * userId 또는 email 중 하나를 사용합니다.
     */
    private User findUser(TeamInviteRequest request) {
        if (request.getUserId() != null) {
            return userRepository.findById(request.getUserId())
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다: " + request.getUserId()));
        } else if (StringUtils.hasText(request.getEmail())) {
            return userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다: " + request.getEmail()));
        } else {
            throw new IllegalArgumentException("userId 또는 email 중 하나는 필수입니다.");
        }
    }

    /**
     * Team 엔티티를 TeamResponse DTO로 변환합니다.
     */
    private TeamResponse toResponse(Team team) {
        TeamResponse response = new TeamResponse();
        response.setId(team.getId());
        response.setName(team.getName());
        response.setCreatedAt(team.getCreatedAt());
        return response;
    }

    /**
     * TeamMember 엔티티를 TeamMemberResponse DTO로 변환합니다.
     */
    private TeamMemberResponse toMemberResponse(TeamMember member) {
        TeamMemberResponse response = new TeamMemberResponse();
        response.setTeamId(member.getTeam().getId());
        response.setTeamName(member.getTeam().getName());
        response.setUserId(member.getUser().getId());
        response.setUserName(member.getUser().getName());
        response.setUserEmail(member.getUser().getEmail());
        response.setRole(member.getRole());
        return response;
    }
}


