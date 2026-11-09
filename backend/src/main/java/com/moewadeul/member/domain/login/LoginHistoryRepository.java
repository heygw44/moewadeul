package com.moewadeul.member.domain.login;

import java.time.Instant;
import java.util.List;

public interface LoginHistoryRepository {

    LoginHistory save(LoginHistory loginHistory);

    List<LoginFailure> findFailuresSinceLastSuccess(String attemptedEmail, Instant after);

    List<LoginFailure> findMemberFailuresSinceLastSuccess(long memberId, Instant after);
}
