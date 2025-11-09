package com.example.sbb.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

/**
 * 팀 생성 요청 DTO
 */
@Getter
@Setter
@Schema(description = "팀 생성 요청 DTO")
public class TeamCreateRequest {
    @Schema(description = "팀 이름", example = "플랫폼팀")
    @NotBlank(message = "팀 이름은 필수입니다.")
    private String name;
}

