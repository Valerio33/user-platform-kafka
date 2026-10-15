package com.aston.userservice.dto;

import java.time.LocalDateTime;

public record UserResponse(
        Long id, String name, String email, Integer age, LocalDateTime createdAt) {}
