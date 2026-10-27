package com.moewadeul.trust.application.identity;

import com.moewadeul.common.error.BusinessException;
import com.moewadeul.trust.domain.TrustErrorCode;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.dao.DataIntegrityViolationException;

final class IdentityUniqueConstraintTranslator {

    private static final String MEMBER_UNIQUE_CONSTRAINT = "uk_identity_verification_member";
    private static final String CI_HASH_UNIQUE_CONSTRAINT = "uk_identity_verification_ci_hash";
    private static final String KEY_MARKER = " for key '";

    private IdentityUniqueConstraintTranslator() {}

    static RuntimeException translate(DataIntegrityViolationException e) {
        String violatedKey =
                violatedKey(NestedExceptionUtils.getMostSpecificCause(e).getMessage());
        if (violatedKey.contains(MEMBER_UNIQUE_CONSTRAINT)) {
            return new BusinessException(TrustErrorCode.IDENTITY_ALREADY_VERIFIED);
        }
        if (violatedKey.contains(CI_HASH_UNIQUE_CONSTRAINT)) {
            return new BusinessException(TrustErrorCode.IDENTITY_CI_DUPLICATED);
        }
        return e;
    }

    // MySQL 메시지는 "Duplicate entry '값' for key '제약'" 형태다. 값 자리에 제약 이름이 들어가도 오판하지 않게 마지막 "for key" 뒤만 본다.
    private static String violatedKey(String message) {
        if (message == null) {
            return "";
        }
        int marker = message.lastIndexOf(KEY_MARKER);
        return marker < 0 ? message : message.substring(marker + KEY_MARKER.length());
    }
}
