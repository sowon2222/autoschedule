package com.example.sbb.dto.request;

import com.example.sbb.domain.Role;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class TeamMemberUpdateRequest {
    private Role role;  // 역할 변경만 가능
    // teamId, userId는 변경 불가 (삭제 후 재생성)
}

