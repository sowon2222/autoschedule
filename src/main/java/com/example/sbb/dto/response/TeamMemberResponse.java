package com.example.sbb.dto.response;

import com.example.sbb.domain.Role;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class TeamMemberResponse {
    private Long teamId;
    private String teamName;
    private Long userId;
    private String userName;
    private String userEmail;
    private Role role;
}

