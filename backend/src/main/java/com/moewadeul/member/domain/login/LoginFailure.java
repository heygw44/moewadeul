package com.moewadeul.member.domain.login;

import java.time.Instant;

public record LoginFailure(Instant failedAt, String ip) {}
