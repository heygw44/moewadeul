package com.moewadeul.admin.api.trust;

import jakarta.validation.constraints.NotBlank;

public record SanctionLiftRequest(@NotBlank String reason) {}
