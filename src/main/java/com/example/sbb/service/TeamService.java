package com.example.sbb.service;

import com.example.sbb.domain.Team;
import com.example.sbb.domain.TeamMember;
import com.example.sbb.domain.TeamMemberId;
import com.example.sbb.domain.User;
import com.example.sbb.domain.Role;
import com.example.sbb.dto.request.TeamCreateRequest;
import com.example.sbb.dto.request.TeamUpdateRequest;
import com.example.sbb.dto.request.TeamInviteRequest;
import com.example.sbb.dto.response.TeamResponse;
import com.example.sbb.dto.response.TeamMemberResponse;
import com.example.sbb.repository.TeamRepository;
import com.example.sbb.repository.TeamMemberRepository;
import com.example.sbb.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class TeamService {
    private final TeamRepository teamRepository;
    private final TeamMemberRepository teamMemberRepository;
    private final UserRepository userRepository;

    public TeamService(TeamRepository teamRepository, TeamMemberRepository teamMemberRepository, UserRepository userRepository) {
        this.teamRepository = teamRepository;
        this.teamMemberRepository = teamMemberRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public TeamResponse createTeam(TeamCreateRequest request) {
        Team team = new Team();
        team.setName(request.getName());
        team.setCreatedAt(OffsetDateTime.now());
        Team saved = teamRepository.save(team);
        return toResponse(saved);
    }

    public TeamResponse findById(Long id) {
        Team team = teamRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("팀을 찾을 수 없습니다: " + id));
        return toResponse(team);
    }

    public List<TeamResponse> findAll() {
        return teamRepository.findAll().stream()
            .map(this::toResponse)
            .collect(Collectors.toList());
    }

    @Transactional
    public TeamResponse updateTeam(Long id, TeamUpdateRequest request) {
        Team team = teamRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("팀을 찾을 수 없습니다: " + id));
        if (request.getName() != null) {
            team.setName(request.getName());
        }
        Team saved = teamRepository.save(team);
        return toResponse(saved);
    }

    @Transactional
    public void deleteTeam(Long id) {
        if (!teamRepository.existsById(id)) {
            throw new IllegalArgumentException("팀을 찾을 수 없습니다: " + id);
        }
        teamRepository.deleteById(id);
    }

    // --- Members ---
    @Transactional
    public TeamMemberResponse inviteMember(Long teamId, TeamInviteRequest request) {
        Team team = teamRepository.findById(teamId)
            .orElseThrow(() -> new IllegalArgumentException("팀을 찾을 수 없습니다: " + teamId));

        User user;
        if (request.getUserId() != null) {
            user = userRepository.findById(request.getUserId())
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다: " + request.getUserId()));
        } else if (request.getEmail() != null) {
            user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다: " + request.getEmail()));
        } else {
            throw new IllegalArgumentException("userId 또는 email 중 하나는 필수입니다.");
        }

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

        return toMemberResponse(member);
    }

    @Transactional(readOnly = true)
    public List<TeamMemberResponse> listMembers(Long teamId) {
        return teamMemberRepository.findByTeam_Id(teamId).stream()
            .map(this::toMemberResponse)
            .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<TeamResponse> findTeamsByUser(Long userId) {
        return teamMemberRepository.findByUser_Id(userId).stream()
            .map(TeamMember::getTeam)
            .map(this::toResponse)
            .collect(Collectors.toList());
    }

    private TeamResponse toResponse(Team team) {
        TeamResponse r = new TeamResponse();
        r.setId(team.getId());
        r.setName(team.getName());
        r.setCreatedAt(team.getCreatedAt());
        return r;
    }

    private TeamMemberResponse toMemberResponse(TeamMember m) {
        TeamMemberResponse r = new TeamMemberResponse();
        r.setTeamId(m.getTeam().getId());
        r.setTeamName(m.getTeam().getName());
        r.setUserId(m.getUser().getId());
        r.setUserName(m.getUser().getName());
        r.setUserEmail(m.getUser().getEmail());
        r.setRole(m.getRole());
        return r;
    }
}


