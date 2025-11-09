package com.example.sbb.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

/**
 * 팀 업데이트 요청 DTO
 */
@Getter
@Setter
@Schema(description = "팀 수정 요청 DTO")
public class TeamUpdateRequest {
    @Schema(description = "팀 이름", example = "AI연구팀")
    private String name;
}

