package com.moewadeul.member.application.login;

import com.moewadeul.member.domain.MemberRole;
import com.moewadeul.member.domain.MemberStatus;
import java.time.Instant;

public record LoginResult(
        long memberId, String nickname, MemberStatus status, MemberRole role, Instant passwordChangedAt) {}
