package com.moewadeul.trust.application.profile;

import com.moewadeul.common.error.BusinessException;
import com.moewadeul.common.profile.AgeGroup;
import com.moewadeul.common.profile.Gender;
import com.moewadeul.common.profile.TrustLevel;
import com.moewadeul.common.time.KoreaTime;
import com.moewadeul.trust.domain.CompanionReviewPolicy;
import com.moewadeul.trust.domain.TrustErrorCode;
import com.moewadeul.trust.domain.companion.CompanionReviewTag;
import com.moewadeul.trust.domain.identity.IdentityVerification;
import com.moewadeul.trust.domain.identity.IdentityVerificationRepository;
import com.moewadeul.trust.domain.profile.CompanionRecord;
import com.moewadeul.trust.domain.profile.TrustLevelPolicy;
import com.moewadeul.trust.infra.profile.TagCountRow;
import com.moewadeul.trust.infra.profile.TrustRecordMapper;
import com.moewadeul.trust.infra.profile.TrustRecordRow;
import java.time.Clock;
import java.time.Instant;
import java.time.Year;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class TrustSummaryService {

    private static final TrustSummary NOT_VERIFIED_LEVEL_ZERO = new TrustSummary(false, TrustLevel.EMAIL_VERIFIED);

    private final IdentityVerificationRepository identityVerificationRepository;
    private final TrustRecordMapper trustRecordMapper;
    private final Clock clock;

    @Transactional(readOnly = true)
    public TrustSummary summarize(long memberId) {
        Instant now = clock.instant();
        return identityVerificationRepository
                .findByMemberId(memberId)
                .map(verification -> summarizeOf(verification, memberId, now))
                .orElse(NOT_VERIFIED_LEVEL_ZERO);
    }

    @Transactional(readOnly = true)
    public void requireLevel(long memberId, TrustLevel minLevel) {
        if (!summarize(memberId).trustLevel().atLeast(minLevel)) {
            throw new BusinessException(TrustErrorCode.TRUST_LEVEL_INSUFFICIENT);
        }
    }

    @Transactional(readOnly = true)
    public TrustDetail detail(long memberId) {
        Instant now = clock.instant();
        Year currentYear = currentYear(now);
        Optional<IdentityVerification> found = identityVerificationRepository.findByMemberId(memberId);
        boolean adult =
                found.map(verification -> verification.isAdult(currentYear)).orElse(false);
        CompanionRecord record = companionRecordOf(memberId, now);
        TrustLevel trustLevel = TrustLevelPolicy.judge(found.isPresent(), adult, record);
        AgeGroup verifiedAgeGroup = found.flatMap(verification -> verification.ageGroupIn(currentYear))
                .orElse(null);
        Gender verifiedGender =
                found.flatMap(IdentityVerification::retainedGender).orElse(null);
        return new TrustDetail(
                found.isPresent(),
                adult,
                trustLevel,
                record.completedCompanions(),
                record.rejoinRate(),
                record.recentEarlyLeaves(),
                !record.recentSanction(),
                topTagsOf(memberId, now),
                verifiedAgeGroup,
                verifiedGender);
    }

    public TrustSummary summarizeOf(IdentityVerification verification, long memberId, Instant now) {
        if (!verification.isAdult(currentYear(now))) {
            return new TrustSummary(true, TrustLevelPolicy.judge(true, false, CompanionRecord.NONE));
        }
        TrustLevel level = TrustLevelPolicy.judge(true, true, companionRecordOf(memberId, now));
        return new TrustSummary(true, level);
    }

    private CompanionRecord companionRecordOf(long memberId, Instant now) {
        Instant recentSince = now.minus(CompanionRecord.RECENT_PERIOD);
        TrustRecordRow row =
                trustRecordMapper.selectCompanionRecord(memberId, CompanionReviewPolicy.revealCutoff(now), recentSince);
        return new CompanionRecord(
                Math.toIntExact(row.completedCompanions()),
                Math.toIntExact(row.receivedReviews()),
                Math.toIntExact(row.rejoinWanted()),
                Math.toIntExact(row.recentEarlyLeaves()),
                row.recentSanction());
    }

    private List<String> topTagsOf(long memberId, Instant now) {
        Map<CompanionReviewTag, Long> counts = new EnumMap<>(CompanionReviewTag.class);
        for (TagCountRow row :
                trustRecordMapper.selectRevealedTagCounts(memberId, CompanionReviewPolicy.revealCutoff(now))) {
            counts.put(row.tag(), row.count());
        }
        return CompanionReviewTag.top(counts).stream().map(Enum::name).toList();
    }

    private static Year currentYear(Instant now) {
        return Year.from(now.atZone(KoreaTime.ZONE));
    }
}
