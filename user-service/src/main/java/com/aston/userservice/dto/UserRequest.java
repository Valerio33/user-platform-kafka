package com.aston.userservice.dto;

import jakarta.validation.constraints.*;

public record UserRequest(
        @NotBlank @Size(max = 100) String name,
        @NotBlank @Email @Size(max = 254) String email,
        @NotNull @Min(0) @Max(150) Integer age) {
    public UserRequest {
        name = name == null ? null : name.strip();
        email = email == null ? null : email.strip();
    }
}
