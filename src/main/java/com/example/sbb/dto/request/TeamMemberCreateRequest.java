package com.example.sbb.dto.request;

import com.example.sbb.domain.Role;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class TeamMemberCreateRequest {
    private Long teamId;
    private Long userId;
    private Role role;  // 기본값 MEMBER
}

