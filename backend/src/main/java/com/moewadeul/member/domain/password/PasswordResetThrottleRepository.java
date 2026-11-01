package com.moewadeul.member.domain.password;

import java.util.Optional;

public interface PasswordResetThrottleRepository {

    Optional<PasswordResetThrottle> findByKeyForUpdate(String key);
}
