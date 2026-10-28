package com.moewadeul.trust.application.identity;

import com.moewadeul.common.error.BusinessException;
import com.moewadeul.common.error.UniqueViolations;
import com.moewadeul.trust.domain.TrustErrorCode;
import org.springframework.dao.DataIntegrityViolationException;

final class IdentityUniqueConstraintTranslator {

    private static final String MEMBER_UNIQUE_CONSTRAINT = "uk_identity_verification_member";
    private static final String CI_HASH_UNIQUE_CONSTRAINT = "uk_identity_verification_ci_hash";

    private IdentityUniqueConstraintTranslator() {}

    static RuntimeException translate(DataIntegrityViolationException e) {
        if (UniqueViolations.violates(e, MEMBER_UNIQUE_CONSTRAINT)) {
            return new BusinessException(TrustErrorCode.IDENTITY_ALREADY_VERIFIED);
        }
        if (UniqueViolations.violates(e, CI_HASH_UNIQUE_CONSTRAINT)) {
            return new BusinessException(TrustErrorCode.IDENTITY_CI_DUPLICATED);
        }
        return e;
    }
}
