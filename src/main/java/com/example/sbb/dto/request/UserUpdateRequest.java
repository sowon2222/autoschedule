package com.example.sbb.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Schema(description = "사용자 수정 요청 DTO")
public class UserUpdateRequest {
    @Schema(description = "사용자 이름", example = "김개발")
    private String name;  // 이메일은 변경 불가 (유니크 키)
}

