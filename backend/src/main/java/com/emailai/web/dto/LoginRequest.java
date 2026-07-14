package com.emailai.web.dto;

import jakarta.validation.constraints.NotBlank;

public record LoginRequest(@NotBlank String masterPassword) {}
