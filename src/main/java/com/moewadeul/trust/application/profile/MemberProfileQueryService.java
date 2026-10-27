package com.moewadeul.trust.application.profile;

import com.moewadeul.member.application.profile.MemberProfile;
import com.moewadeul.member.application.profile.MemberProfileService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class MemberProfileQueryService {

    private final MemberProfileService memberProfileService;
    private final TrustSummaryService trustSummaryService;

    public MemberTrustProfile find(long memberId) {
        return merge(memberProfileService.find(memberId));
    }

    public MemberTrustProfile findIncludingWithdrawn(long memberId) {
        return merge(memberProfileService.findIncludingWithdrawn(memberId));
    }

    private MemberTrustProfile merge(MemberProfile profile) {
        long memberId = profile.memberId();
        TrustDetail detail = trustSummaryService.detail(memberId);
        return new MemberTrustProfile(
                profile.memberId(),
                profile.nickname(),
                firstNonNull(detail.verifiedAgeGroup(), profile.selfAgeGroup()),
                detail.verifiedAgeGroup() != null,
                firstNonNull(detail.verifiedGender(), profile.selfGender()),
                detail.verifiedGender() != null,
                detail.trustLevel(),
                detail.completedCompanions(),
                detail.rejoinRate(),
                detail.topTags());
    }

    private static String firstNonNull(String verified, String selfReported) {
        if (verified != null) {
            return verified;
        }
        return selfReported;
    }
}
