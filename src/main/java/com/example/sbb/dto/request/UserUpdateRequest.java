package com.example.sbb.dto.request;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UserUpdateRequest {
    private String name;  // 이메일은 변경 불가 (유니크 키)
}

