package com.example.sbb.dto.request;

import com.example.sbb.domain.Role;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class TeamInviteRequest {
    private Long userId;        // userId 또는 email 둘 중 하나 사용
    private String email;
    private Role role;          // null이면 MEMBER 처리
}



