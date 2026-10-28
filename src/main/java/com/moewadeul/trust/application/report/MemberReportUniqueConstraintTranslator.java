package com.moewadeul.trust.application.report;

import com.moewadeul.common.error.BusinessException;
import com.moewadeul.common.error.UniqueViolations;
import com.moewadeul.trust.domain.TrustErrorCode;
import org.springframework.dao.DataIntegrityViolationException;

final class MemberReportUniqueConstraintTranslator {

    private static final String UNIQUE_CONSTRAINT = "uk_member_report_reporter_target_basecamp_kind";

    private MemberReportUniqueConstraintTranslator() {}

    static RuntimeException translate(DataIntegrityViolationException e) {
        if (UniqueViolations.violates(e, UNIQUE_CONSTRAINT)) {
            return new BusinessException(TrustErrorCode.REPORT_DUPLICATED);
        }
        return e;
    }
}
