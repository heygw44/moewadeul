package com.moewadeul.trust.api.profile;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.moewadeul.trust.application.profile.TrustDetail;
import com.moewadeul.trust.domain.profile.CompanionRecord;

public record MyTrustResponse(
        int trustLevel,
        boolean identityVerified,
        @JsonInclude(JsonInclude.Include.NON_NULL) NextLevel nextLevel) {

    private static final int MAX_LEVEL = 2;

    public static MyTrustResponse from(TrustDetail detail) {
        NextLevel nextLevel = null;
        if (detail.trustLevel() < MAX_LEVEL) {
            nextLevel = new NextLevel(
                    MAX_LEVEL,
                    new Progress(detail.completedCompanions(), CompanionRecord.REQUIRED_COMPLETED_COMPANIONS),
                    new RateProgress(detail.rejoinRate(), CompanionRecord.REQUIRED_REJOIN_PERCENT),
                    new Limit(detail.recentEarlyLeaves(), CompanionRecord.EARLY_LEAVE_LIMIT),
                    detail.noRecentSanction());
        }
        return new MyTrustResponse(detail.trustLevel(), detail.identityVerified(), nextLevel);
    }

    public record NextLevel(
            int level,
            Progress completedCompanions,
            RateProgress rejoinRate,
            Limit recentEarlyLeaves,
            boolean noRecentSanction) {}

    public record Progress(int current, int required) {}

    public record RateProgress(Integer current, int required) {}

    public record Limit(int current, int limit) {}
}
