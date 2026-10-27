package com.moewadeul.trust.application.companion;

import com.moewadeul.common.error.BusinessException;
import com.moewadeul.common.error.CommonErrorCode;
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
import java.util.function.Function;
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
    public CompanionReviewPage<ReceivedCompanionReview> received(long memberId, int page, int size) {
        Instant now = clock.instant();
        List<CompanionReviewReceivedRow> rows =
                companionReviewMapper.selectReceived(memberId, offsetOf(page, size), size + 1);
        return pageOf(rows, page, size, row -> toReceived(row, now));
    }

    @Transactional(readOnly = true)
    public CompanionReviewPage<PublicCompanionReview> forMember(long targetMemberId, int page, int size) {
        memberProfileService.find(targetMemberId);
        Instant revealCutoff = CompanionReviewPolicy.revealCutoff(clock.instant());
        List<CompanionReviewPublicRow> rows = companionReviewMapper.selectPublicByReviewee(
                targetMemberId, revealCutoff, offsetOf(page, size), size + 1);
        return pageOf(
                rows,
                page,
                size,
                row -> new PublicCompanionReview(
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

    private static <R, T> CompanionReviewPage<T> pageOf(List<R> rows, int page, int size, Function<R, T> mapper) {
        boolean hasNext = rows.size() > size;
        List<T> content = rows.stream().limit(size).map(mapper).toList();
        return new CompanionReviewPage<>(content, page, size, hasNext);
    }

    private static long offsetOf(int page, int size) {
        return (long) page * size;
    }
}
