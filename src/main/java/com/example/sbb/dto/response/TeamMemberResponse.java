package com.example.sbb.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import com.example.sbb.domain.Role;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 팀 구성원 정보 응답 DTO
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "팀 구성원 응답 DTO")
public class TeamMemberResponse {
    @Schema(description = "팀 ID", example = "1")
    private Long teamId;
    @Schema(description = "팀 이름", example = "플랫폼팀")
    private String teamName;
    @Schema(description = "사용자 ID", example = "5")
    private Long userId;
    @Schema(description = "사용자 이름", example = "김개발")
    private String userName;
    @Schema(description = "사용자 이메일", example = "member@example.com")
    private String userEmail;
    @Schema(description = "역할", example = "MEMBER")
    private Role role;
}

