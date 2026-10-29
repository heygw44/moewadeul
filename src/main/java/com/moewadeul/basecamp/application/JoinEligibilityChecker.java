package com.moewadeul.basecamp.application;

import com.moewadeul.basecamp.domain.Basecamp;
import com.moewadeul.basecamp.domain.BasecampErrorCode;
import com.moewadeul.basecamp.domain.JoinEligibilityPolicy;
import com.moewadeul.basecamp.domain.JoinEligibilityPolicy.Applicant;
import com.moewadeul.basecamp.domain.JoinUnmetReason;
import com.moewadeul.basecamp.domain.PriorRelation;
import com.moewadeul.basecamp.infra.query.BasecampSearchMapper;
import com.moewadeul.basecamp.infra.query.InProgressScheduleRow;
import com.moewadeul.common.error.BusinessException;
import com.moewadeul.trust.application.profile.TrustSummaryService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class JoinEligibilityChecker {

    private final TrustSummaryService trustSummaryService;
    private final BasecampSearchMapper basecampSearchMapper;

    public Applicant applicantOf(long memberId) {
        return JoinApplicants.from(trustSummaryService.detail(memberId));
    }

    public void requireEligible(Basecamp basecamp, Applicant applicant, long memberId) {
        boolean dateConflict = hasDateConflict(basecamp, memberId);
        List<JoinUnmetReason> reasons = JoinEligibilityPolicy.unmetReasons(
                basecamp.getJoinCondition(), applicant, dateConflict, PriorRelation.NONE);
        if (!reasons.isEmpty()) {
            throw toException(reasons.get(0));
        }
    }

    public JoinEligibility judge(Basecamp basecamp, long viewerId) {
        Applicant applicant = applicantOf(viewerId);
        PriorRelation prior = basecampSearchMapper.selectViewerHistory(viewerId, List.of(basecamp.getId())).stream()
                .findFirst()
                .map(history -> PriorRelation.of(history.memberStatus(), history.applicationStatus()))
                .orElse(PriorRelation.NONE);
        return JoinEligibility.of(JoinEligibilityPolicy.unmetReasons(
                basecamp.getJoinCondition(), applicant, hasDateConflict(basecamp, viewerId), prior));
    }

    private boolean hasDateConflict(Basecamp basecamp, long memberId) {
        List<InProgressScheduleRow> schedules = basecampSearchMapper.selectInProgressSchedules(memberId);
        return schedules.stream()
                .filter(schedule -> schedule.basecampId() != basecamp.getId())
                .anyMatch(schedule -> JoinEligibilityPolicy.overlaps(
                        basecamp.getStartDate(), basecamp.getEndDate(), schedule.startDate(), schedule.endDate()));
    }

    private static BusinessException toException(JoinUnmetReason reason) {
        return switch (reason) {
            case TRUST_LEVEL, AGE_GROUP, GENDER -> new BusinessException(BasecampErrorCode.BASECAMP_CONDITION_NOT_MET);
            case DATE_CONFLICT -> new BusinessException(BasecampErrorCode.BASECAMP_DATE_CONFLICT);
            case ALREADY_JOINED -> new BusinessException(BasecampErrorCode.BASECAMP_ALREADY_APPLIED);
            case REAPPLY_NOT_ALLOWED -> new BusinessException(BasecampErrorCode.BASECAMP_REAPPLY_NOT_ALLOWED);
        };
    }
}
