package com.moewadeul.common.moderation;

import com.moewadeul.common.error.BusinessException;
import com.moewadeul.common.error.ErrorCode;

public final class ModerationTransition {

    // 박지(F-08), 커뮤니티 글·댓글(CM-07), 추억 기록(MM-10)이 같은 수를 쓴다.
    public static final long REPORT_THRESHOLD = 5;

    private ModerationTransition() {}

    public static boolean reachesPendingReview(ModerationStatus current, long unreviewedReportCount) {
        if (unreviewedReportCount < REPORT_THRESHOLD) {
            return false;
        }
        if (!current.isActive()) {
            throw new IllegalStateException("정상 상태에서만 검토 대기로 바꿀 수 있습니다. 현재 상태: " + current.name());
        }
        return true;
    }

    public static void requireHideable(ModerationStatus current, ErrorCode invalidState) {
        if (!current.isActive() && !current.isPendingReview()) {
            throw new BusinessException(invalidState);
        }
    }

    public static void requireRestorable(ModerationStatus current, ErrorCode invalidState) {
        if (!current.isPendingReview() && !current.isHidden()) {
            throw new BusinessException(invalidState);
        }
    }
}
