package com.example.sbb.repository;

import com.example.sbb.domain.TeamMember;
import com.example.sbb.domain.TeamMemberId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TeamMemberRepository extends JpaRepository<TeamMember, TeamMemberId> {
    List<TeamMember> findByTeam_Id(Long teamId);
    List<TeamMember> findByUser_Id(Long userId);
}



