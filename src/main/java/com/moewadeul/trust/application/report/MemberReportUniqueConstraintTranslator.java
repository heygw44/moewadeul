package com.moewadeul.trust.application.report;

import com.moewadeul.common.error.BusinessException;
import com.moewadeul.trust.domain.TrustErrorCode;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.dao.DataIntegrityViolationException;

final class MemberReportUniqueConstraintTranslator {

    private static final String UNIQUE_CONSTRAINT = "uk_member_report_reporter_target_basecamp_kind";
    private static final String KEY_MARKER = " for key '";

    private MemberReportUniqueConstraintTranslator() {}

    static RuntimeException translate(DataIntegrityViolationException e) {
        String violatedKey =
                violatedKey(NestedExceptionUtils.getMostSpecificCause(e).getMessage());
        if (violatedKey.contains(UNIQUE_CONSTRAINT)) {
            return new BusinessException(TrustErrorCode.REPORT_DUPLICATED);
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
