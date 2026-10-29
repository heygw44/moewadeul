package com.moewadeul.basecamp.domain;

import java.util.ArrayList;
import java.util.List;

public final class JoinEligibilityPolicy {

    public static final int BASE_TRUST_LEVEL = 1;

    private JoinEligibilityPolicy() {}

    public record Applicant(int trustLevel, Integer verifiedAgeGroup, JoinGender verifiedGender) {}

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
                .anyMatch(schedule -> overlaps(target, schedule));
    }

    private static boolean overlaps(BasecampSchedule a, BasecampSchedule b) {
        return !a.startDate().isAfter(b.endDate()) && !a.endDate().isBefore(b.startDate());
    }

    private static boolean meetsTrustLevel(JoinCondition condition, Applicant applicant) {
        int required =
                Math.max(BASE_TRUST_LEVEL, condition.getMinTrustLevel() == null ? 0 : condition.getMinTrustLevel());
        return applicant.trustLevel() >= required;
    }

    private static boolean meetsAgeGroup(JoinCondition condition, Applicant applicant) {
        Integer min = condition.getAgeGroupMin();
        Integer max = condition.getAgeGroupMax();
        if (min == null || max == null) {
            return true;
        }
        Integer ageGroup = applicant.verifiedAgeGroup();
        return ageGroup != null && ageGroup >= min && ageGroup <= max;
    }

    private static boolean meetsGender(JoinCondition condition, Applicant applicant) {
        if (!condition.isSameGenderOnly()) {
            return true;
        }
        return applicant.verifiedGender() != null && applicant.verifiedGender() == condition.getRequiredGender();
    }
}
