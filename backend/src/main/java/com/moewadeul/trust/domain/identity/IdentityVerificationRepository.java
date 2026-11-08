package com.moewadeul.trust.domain.identity;

import java.util.Optional;

public interface IdentityVerificationRepository {

    Optional<IdentityVerification> findByMemberId(long memberId);

    boolean existsByMemberId(long memberId);

    boolean existsByCiHash(CiHash ciHash);
}
