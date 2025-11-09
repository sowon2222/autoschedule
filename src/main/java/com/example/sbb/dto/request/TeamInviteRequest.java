package com.example.sbb.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
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
@Schema(description = "팀 구성원 초대 요청 DTO")
public class TeamInviteRequest {
    @Schema(description = "사용자 ID (userId 또는 email 중 하나 입력)", example = "5")
    private Long userId;

    @Schema(description = "사용자 이메일 (userId 또는 email 중 하나 입력)", example = "member@example.com")
    private String email;

    @Schema(description = "팀 내 역할", example = "MEMBER")
    private Role role;

    @Schema(hidden = true)
    @AssertTrue(message = "userId 또는 email 중 하나는 필수입니다.")
    private boolean isValidRequest() {
        return userId != null || (email != null && !email.trim().isEmpty());
    }
}



