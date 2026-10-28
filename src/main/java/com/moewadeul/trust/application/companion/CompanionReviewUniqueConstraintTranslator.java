package com.moewadeul.trust.application.companion;

import com.moewadeul.common.error.BusinessException;
import com.moewadeul.common.error.UniqueViolations;
import com.moewadeul.trust.domain.TrustErrorCode;
import org.springframework.dao.DataIntegrityViolationException;

final class CompanionReviewUniqueConstraintTranslator {

    private static final String UNIQUE_CONSTRAINT = "uk_companion_review_basecamp_reviewer_reviewee";

    private CompanionReviewUniqueConstraintTranslator() {}

    static RuntimeException translate(DataIntegrityViolationException e) {
        if (UniqueViolations.violates(e, UNIQUE_CONSTRAINT)) {
            return new BusinessException(TrustErrorCode.COMPANION_REVIEW_DUPLICATED);
        }
        return e;
    }
}
