package com.moewadeul.basecamp.application;

import com.moewadeul.basecamp.domain.JoinEligibilityPolicy.Applicant;
import com.moewadeul.trust.application.profile.TrustDetail;

public final class JoinApplicants {

    private JoinApplicants() {}

    public static Applicant from(TrustDetail trust) {
        return new Applicant(trust.trustLevel(), trust.verifiedAgeGroup(), trust.verifiedGender());
    }
}
