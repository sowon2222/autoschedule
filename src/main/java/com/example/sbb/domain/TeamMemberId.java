package com.example.sbb.domain;

import jakarta.persistence.Embeddable;
import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;
import java.util.Objects;

/**
 * 팀 구성원 복합 키
 * TeamMember 엔티티의 기본 키로 사용됩니다.
 * teamId와 userId의 조합으로 고유성을 보장합니다.
 */
@Embeddable
@Getter
@Setter
public class TeamMemberId implements Serializable {

    private Long teamId;
    private Long userId;

    public TeamMemberId() {
    }

    public TeamMemberId(Long teamId, Long userId) {
        this.teamId = teamId;
        this.userId = userId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TeamMemberId that = (TeamMemberId) o;
        return Objects.equals(teamId, that.teamId) && Objects.equals(userId, that.userId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(teamId, userId);
    }
}


