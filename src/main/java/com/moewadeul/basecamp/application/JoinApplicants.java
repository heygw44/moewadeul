package com.moewadeul.basecamp.application;

import com.moewadeul.basecamp.domain.JoinEligibilityPolicy.Applicant;
import com.moewadeul.basecamp.domain.JoinGender;
import com.moewadeul.trust.application.profile.TrustDetail;

public final class JoinApplicants {

    private JoinApplicants() {}

    public static Applicant from(TrustDetail trust) {
        Integer ageGroup = trust.verifiedAgeGroup() == null ? null : toAgeGroupNumber(trust.verifiedAgeGroup());
        JoinGender gender = trust.verifiedGender() == null ? null : JoinGender.valueOf(trust.verifiedGender());
        return new Applicant(trust.trustLevel(), ageGroup, gender);
    }

    private static int toAgeGroupNumber(String ageGroup) {
        return switch (ageGroup) {
            case "TWENTIES" -> 20;
            case "THIRTIES" -> 30;
            case "FORTIES" -> 40;
            case "FIFTIES" -> 50;
            case "SIXTIES_PLUS" -> 60;
            default -> throw new IllegalStateException("알 수 없는 연령대입니다: " + ageGroup);
        };
    }
}
