package com.example.sbb.repository;

import com.example.sbb.domain.TeamMember;
import com.example.sbb.domain.TeamMemberId;
import com.example.sbb.dto.response.TeamMemberResponse;
import com.example.sbb.dto.response.TeamResponse;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

/**
 * 팀 구성원 Repository
 */
public interface TeamMemberRepository extends JpaRepository<TeamMember, TeamMemberId> {
    /**
     * 팀 ID로 팀 구성원 목록을 조회합니다. (DTO 프로젝션)
     *
     * @param teamId 팀 ID
     * @return 팀 구성원 목록
     */
    @Query("""
        SELECT new com.example.sbb.dto.response.TeamMemberResponse(
            tm.team.id,
            tm.team.name,
            tm.user.id,
            tm.user.name,
            tm.user.email,
            tm.role
        )
        FROM TeamMember tm
        LEFT JOIN tm.team
        LEFT JOIN tm.user
        WHERE tm.team.id = :teamId
    """)
    List<TeamMemberResponse> findResponsesByTeamId(@Param("teamId") Long teamId);

    /**
     * 사용자 ID로 해당 사용자가 속한 팀 목록을 조회합니다. (DTO 프로젝션)
     *
     * @param userId 사용자 ID
     * @return 팀 목록
     */
    @Query("""
        SELECT new com.example.sbb.dto.response.TeamResponse(
            tm.team.id,
            tm.team.name,
            tm.team.createdAt
        )
        FROM TeamMember tm
        LEFT JOIN tm.team
        WHERE tm.user.id = :userId
    """)
    List<TeamResponse> findTeamResponsesByUserId(@Param("userId") Long userId);
}



