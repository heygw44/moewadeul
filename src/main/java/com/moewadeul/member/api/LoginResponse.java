package com.moewadeul.member.api;

import com.moewadeul.member.application.LoginResult;
import com.moewadeul.member.domain.MemberRole;
import com.moewadeul.member.domain.MemberStatus;

public record LoginResponse(Long memberId, String nickname, MemberStatus status, MemberRole role) {

    public static LoginResponse from(LoginResult result) {
        return new LoginResponse(result.memberId(), result.nickname(), result.status(), result.role());
    }
}
