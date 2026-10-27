package com.moewadeul.member.domain.login;

import java.time.Instant;
import java.util.List;

public interface LoginHistoryRepository {

    LoginHistory save(LoginHistory loginHistory);

    List<Instant> findFailureTimesSinceLastSuccess(String attemptedEmail, Instant after);
}
