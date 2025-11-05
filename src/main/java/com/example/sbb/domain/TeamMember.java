package com.example.sbb.domain;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * 팀 구성원 엔티티
 * Team과 User의 다대다 관계를 표현하는 중간 엔티티입니다.
 * 복합 키(TeamMemberId)를 사용하여 team_id와 user_id를 조합한 기본 키를 가집니다.
 */
@Entity
@Table(name = "team_member")
@Getter
@Setter
public class TeamMember {

    @EmbeddedId
    private TeamMemberId id;

    @MapsId("teamId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "team_id", nullable = false)
    private Team team;

    @MapsId("userId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role = Role.MEMBER;
}


