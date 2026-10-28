package com.moewadeul.trust.infra.identity;

import com.moewadeul.trust.domain.identity.CiHash;
import com.moewadeul.trust.domain.identity.IdentityVerification;
import com.moewadeul.trust.domain.identity.IdentityVerificationRepository;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IdentityVerificationJpaRepository
        extends JpaRepository<IdentityVerification, Long>, IdentityVerificationRepository {

    @Override
    IdentityVerification saveAndFlush(IdentityVerification identityVerification);

    @Override
    Optional<IdentityVerification> findByMemberId(long memberId);

    @Override
    boolean existsByMemberId(long memberId);

    @Override
    boolean existsByCiHash(CiHash ciHash);
}
