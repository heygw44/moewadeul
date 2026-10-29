package com.moewadeul.trust.application.companion;

import com.moewadeul.common.error.BusinessException;
import com.moewadeul.common.error.CommonErrorCode;
import com.moewadeul.common.paging.PageSlice;
import com.moewadeul.member.application.profile.MemberProfileService;
import com.moewadeul.trust.application.profile.TrustSummaryService;
import com.moewadeul.trust.domain.CompanionReviewPolicy;
import com.moewadeul.trust.infra.companion.CompanionReviewMapper;
import com.moewadeul.trust.infra.companion.CompanionReviewPendingRow;
import com.moewadeul.trust.infra.companion.CompanionReviewPublicRow;
import com.moewadeul.trust.infra.companion.CompanionReviewReceivedRow;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CompanionReviewQueryService {

    private final TrustSummaryService trustSummaryService;
    private final MemberProfileService memberProfileService;
    private final CompanionReviewMapper companionReviewMapper;
    private final Clock clock;

    @Transactional(readOnly = true)
    public List<PendingCompanionReview> pending(long memberId) {
        if (trustSummaryService.detail(memberId).trustLevel() < CompanionReviewPolicy.MIN_TRUST_LEVEL) {
            throw new BusinessException(CommonErrorCode.TRUST_LEVEL_INSUFFICIENT);
        }
        Instant writableSince = CompanionReviewPolicy.revealCutoff(clock.instant());
        Map<Long, List<CompanionReviewPendingRow>> byBasecamp =
                companionReviewMapper.selectPending(memberId, writableSince).stream()
                        .collect(Collectors.groupingBy(
                                CompanionReviewPendingRow::basecampId, LinkedHashMap::new, Collectors.toList()));
        return byBasecamp.values().stream()
                .map(CompanionReviewQueryService::toPending)
                .toList();
    }

    private static PendingCompanionReview toPending(List<CompanionReviewPendingRow> rows) {
        CompanionReviewPendingRow first = rows.get(0);
        List<PendingCompanionReview.Target> targets = rows.stream()
                .map(row -> new PendingCompanionReview.Target(row.targetId(), row.targetNickname()))
                .toList();
        return new PendingCompanionReview(
                first.basecampId(),
                first.basecampTitle(),
                first.completedAt(),
                CompanionReviewPolicy.deadlineOf(first.completedAt()),
                targets);
    }

    @Transactional(readOnly = true)
    public PageSlice<ReceivedCompanionReview> received(long memberId, int page, int size) {
        Instant now = clock.instant();
        List<CompanionReviewReceivedRow> rows = companionReviewMapper.selectReceived(
                memberId, PageSlice.offsetOf(page, size), PageSlice.fetchSize(size));
        return PageSlice.of(rows, page, size).map(row -> toReceived(row, now));
    }

    @Transactional(readOnly = true)
    public PageSlice<PublicCompanionReview> forMember(long targetMemberId, int page, int size) {
        memberProfileService.find(targetMemberId);
        Instant revealCutoff = CompanionReviewPolicy.revealCutoff(clock.instant());
        List<CompanionReviewPublicRow> rows = companionReviewMapper.selectPublicByReviewee(
                targetMemberId, revealCutoff, PageSlice.offsetOf(page, size), PageSlice.fetchSize(size));
        return PageSlice.of(rows, page, size)
                .map(row -> new PublicCompanionReview(
                        CompanionReviewTags.parse(row.tags()), row.comment(), row.createdAt()));
    }

    private static ReceivedCompanionReview toReceived(CompanionReviewReceivedRow row, Instant now) {
        if (!CompanionReviewPolicy.isRevealed(row.reverseWritten(), row.completedAt(), now)) {
            return ReceivedCompanionReview.sealed(row.basecampId());
        }
        return new ReceivedCompanionReview(
                row.basecampId(),
                true,
                row.reviewId(),
                row.basecampTitle(),
                row.reviewerId(),
                row.reviewerNickname(),
                row.rejoinWanted(),
                CompanionReviewTags.parse(row.tags()),
                row.comment(),
                row.createdAt());
    }
}
