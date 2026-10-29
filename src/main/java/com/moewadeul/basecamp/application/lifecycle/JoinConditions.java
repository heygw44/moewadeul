package com.moewadeul.basecamp.application.lifecycle;

import com.moewadeul.basecamp.domain.JoinCondition;
import com.moewadeul.common.error.BusinessException;
import com.moewadeul.common.error.CommonErrorCode;
import com.moewadeul.common.profile.AgeGroup;
import com.moewadeul.common.profile.TrustLevel;
import com.moewadeul.trust.application.profile.TrustDetail;

final class JoinConditions {

    private JoinConditions() {}

    static JoinCondition from(BasecampOpenCommand.JoinConditionCommand requested, TrustDetail trust) {
        if (requested == null) {
            return JoinCondition.none();
        }
        try {
            JoinCondition condition = JoinCondition.none();
            if (requested.minTrustLevel() != null) {
                condition = condition.withMinTrustLevel(
                        TrustLevel.find(requested.minTrustLevel()).orElse(null));
            }
            condition = withAgeGroupRange(condition, requested);
            if (requested.sameGenderOnly()) {
                condition = condition.withSameGenderOnly(trust.verifiedGender());
            }
            return condition;
        } catch (IllegalArgumentException e) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT, e.getMessage());
        }
    }

    private static JoinCondition withAgeGroupRange(
            JoinCondition condition, BasecampOpenCommand.JoinConditionCommand requested) {
        Integer min = requested.ageGroupMin();
        Integer max = requested.ageGroupMax();
        if (min == null && max == null) {
            return condition;
        }
        if (min == null || max == null) {
            throw new IllegalArgumentException("연령대 하한과 상한은 함께 보내야 합니다.");
        }
        return condition.withAgeGroupRange(AgeGroup.ofDecade(min), AgeGroup.ofDecade(max));
    }
}
