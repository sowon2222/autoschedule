package com.example.sbb.repository;

import com.example.sbb.domain.TeamMember;
import com.example.sbb.domain.TeamMemberId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * 팀 구성원 Repository
 */
public interface TeamMemberRepository extends JpaRepository<TeamMember, TeamMemberId> {
    /**
     * 팀 ID로 팀 구성원 목록을 조회합니다.
     *
     * @param teamId 팀 ID
     * @return 팀 구성원 목록
     */
    List<TeamMember> findByTeam_Id(Long teamId);

    /**
     * 사용자 ID로 해당 사용자가 속한 팀 구성원 목록을 조회합니다.
     *
     * @param userId 사용자 ID
     * @return 팀 구성원 목록
     */
    List<TeamMember> findByUser_Id(Long userId);
}



