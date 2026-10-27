package com.moewadeul.program.api.enrollment;

import jakarta.validation.constraints.NotNull;

public record ProgramPayRequest(@NotNull PaymentMethod method) {}
