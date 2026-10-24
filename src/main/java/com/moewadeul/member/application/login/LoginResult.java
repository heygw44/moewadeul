package com.moewadeul.member.application.login;

import com.moewadeul.member.domain.MemberRole;
import com.moewadeul.member.domain.MemberStatus;
import java.time.Instant;

/**
 * @param passwordChangedAt 로그인 요청이 회원을 읽은 때의 비밀번호 변경 시각. 비밀번호를 바꾼 적이 없으면 null이다.
 *     세션을 만든 뒤 {@link MemberLoginService#ensureStillLoginable}가 이 값과 지금 값을 비교한다.
 */
public record LoginResult(
        long memberId, String nickname, MemberStatus status, MemberRole role, Instant passwordChangedAt) {}
