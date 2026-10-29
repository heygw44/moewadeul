package com.moewadeul.basecamp.domain;

import com.moewadeul.common.profile.AgeGroup;
import com.moewadeul.common.profile.Gender;
import com.moewadeul.common.profile.TrustLevel;
import java.util.ArrayList;
import java.util.List;

public final class JoinEligibilityPolicy {

    public static final TrustLevel BASE_TRUST_LEVEL = TrustLevel.IDENTITY_VERIFIED;

    private JoinEligibilityPolicy() {}

    public record Applicant(TrustLevel trustLevel, AgeGroup verifiedAgeGroup, Gender verifiedGender) {}

    public static List<JoinUnmetReason> unmetReasons(
            JoinCondition condition, Applicant applicant, boolean dateConflict, PriorRelation priorRelation) {
        List<JoinUnmetReason> reasons = new ArrayList<>();
        if (!meetsTrustLevel(condition, applicant)) {
            reasons.add(JoinUnmetReason.TRUST_LEVEL);
        }
        if (!meetsAgeGroup(condition, applicant)) {
            reasons.add(JoinUnmetReason.AGE_GROUP);
        }
        if (!meetsGender(condition, applicant)) {
            reasons.add(JoinUnmetReason.GENDER);
        }
        if (dateConflict) {
            reasons.add(JoinUnmetReason.DATE_CONFLICT);
        }
        if (priorRelation == PriorRelation.ACTIVE) {
            reasons.add(JoinUnmetReason.ALREADY_JOINED);
        }
        if (priorRelation == PriorRelation.BLOCKED) {
            reasons.add(JoinUnmetReason.REAPPLY_NOT_ALLOWED);
        }
        return List.copyOf(reasons);
    }

    public static boolean hasDateConflict(BasecampSchedule target, List<BasecampSchedule> inProgress) {
        return inProgress.stream()
                .filter(schedule -> schedule.basecampId() != target.basecampId())
                .anyMatch(schedule -> target.schedule().sharesDateWith(schedule.schedule()));
    }

    private static boolean meetsTrustLevel(JoinCondition condition, Applicant applicant) {
        TrustLevel minimum = condition.getMinTrustLevel();
        return applicant.trustLevel().atLeast(BASE_TRUST_LEVEL)
                && (minimum == null || applicant.trustLevel().atLeast(minimum));
    }

    private static boolean meetsAgeGroup(JoinCondition condition, Applicant applicant) {
        AgeGroup min = condition.getAgeGroupMin();
        AgeGroup max = condition.getAgeGroupMax();
        if (min == null || max == null) {
            return true;
        }
        AgeGroup ageGroup = applicant.verifiedAgeGroup();
        return ageGroup != null && ageGroup.compareTo(min) >= 0 && ageGroup.compareTo(max) <= 0;
    }

    private static boolean meetsGender(JoinCondition condition, Applicant applicant) {
        if (!condition.isSameGenderOnly()) {
            return true;
        }
        return applicant.verifiedGender() != null && applicant.verifiedGender() == condition.getRequiredGender();
    }
}
