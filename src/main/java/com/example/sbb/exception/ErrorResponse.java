package com.example.sbb.exception;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ErrorResponse {
    private String message;
    private String error;

    public ErrorResponse(String message, String error) {
        this.message = message;
        this.error = error;
    }
}

