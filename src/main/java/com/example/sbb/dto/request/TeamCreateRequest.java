package com.example.sbb.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

/**
 * 팀 생성 요청 DTO
 */
@Getter
@Setter
public class TeamCreateRequest {
    @NotBlank(message = "팀 이름은 필수입니다.")
    private String name;
}

