package com.moewadeul.member.api.signup;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record DisposableEmailDomainAdminRequest(
        @NotNull
        @Size(max = 253)
        @Pattern(regexp = "[A-Za-z0-9](?:[A-Za-z0-9-]*[A-Za-z0-9])?(?:\\.[A-Za-z0-9](?:[A-Za-z0-9-]*[A-Za-z0-9])?)+")
        String domain) {}
