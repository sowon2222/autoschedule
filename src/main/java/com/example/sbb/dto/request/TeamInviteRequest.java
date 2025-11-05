package com.example.sbb.dto.request;

import com.example.sbb.domain.Role;
import jakarta.validation.constraints.AssertTrue;
import lombok.Getter;
import lombok.Setter;

/**
 * 팀 구성원 초대 요청 DTO
 * userId 또는 email 중 하나는 필수입니다.
 */
@Getter
@Setter
public class TeamInviteRequest {
    /**
     * 사용자 ID (userId 또는 email 중 하나 사용)
     */
    private Long userId;

    /**
     * 사용자 이메일 (userId 또는 email 중 하나 사용)
     */
    private String email;

    /**
     * 팀 내 역할 (null이면 MEMBER로 설정)
     */
    private Role role;

    /**
     * userId 또는 email 중 하나는 반드시 제공되어야 합니다.
     */
    @AssertTrue(message = "userId 또는 email 중 하나는 필수입니다.")
    private boolean isValidRequest() {
        return userId != null || (email != null && !email.trim().isEmpty());
    }
}



