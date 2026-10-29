package com.moewadeul.trust.application.companion;

import com.moewadeul.common.error.BusinessException;
import com.moewadeul.common.error.UniqueViolations;
import com.moewadeul.trust.application.profile.TrustSummaryService;
import com.moewadeul.trust.domain.CompanionReviewPolicy;
import com.moewadeul.trust.domain.TrustErrorCode;
import com.moewadeul.trust.domain.companion.CompanionReview;
import com.moewadeul.trust.domain.companion.CompanionReviewRepository;
import com.moewadeul.trust.infra.companion.CompanionReviewMapper;
import com.moewadeul.trust.infra.companion.CompanionReviewWriteContextRow;
import java.time.Clock;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CompanionReviewCommandService {

    private static final String UNIQUE_CONSTRAINT = "uk_companion_review_basecamp_reviewer_reviewee";

    private final TrustSummaryService trustSummaryService;
    private final CompanionReviewRepository companionReviewRepository;
    private final CompanionReviewMapper companionReviewMapper;
    private final Clock clock;

    @Transactional
    public long write(long reviewerId, long basecampId, CompanionReviewWriteCommand command) {
        trustSummaryService.requireLevel(reviewerId, CompanionReviewPolicy.MIN_TRUST_LEVEL);
        CompanionReviewWriteContextRow context = companionReviewMapper
                .selectWriteContext(basecampId, reviewerId, command.revieweeId())
                .orElseThrow(BusinessException::notFound);
        if (!CompanionReviewPolicy.isEligible(
                context.basecampCompleted(),
                context.completedAt(),
                context.reviewerActive(),
                context.revieweeActive(),
                reviewerId,
                command.revieweeId())) {
            throw new BusinessException(TrustErrorCode.COMPANION_REVIEW_NOT_ELIGIBLE);
        }
        Instant now = clock.instant();
        if (!CompanionReviewPolicy.isWritable(context.completedAt(), now)) {
            throw new BusinessException(TrustErrorCode.COMPANION_REVIEW_DEADLINE_PASSED);
        }
        if (context.alreadyWritten()) {
            throw new BusinessException(TrustErrorCode.COMPANION_REVIEW_DUPLICATED);
        }
        return save(newReview(basecampId, reviewerId, command, now)).getId();
    }

    private static CompanionReview newReview(
            long basecampId, long reviewerId, CompanionReviewWriteCommand command, Instant now) {
        return CompanionReview.write(
                basecampId,
                reviewerId,
                command.revieweeId(),
                command.rejoinWanted(),
                command.tags(),
                command.comment(),
                now);
    }

    private CompanionReview save(CompanionReview review) {
        try {
            return companionReviewRepository.saveAndFlush(review);
        } catch (DataIntegrityViolationException e) {
            throw UniqueViolations.translate(e, UNIQUE_CONSTRAINT, TrustErrorCode.COMPANION_REVIEW_DUPLICATED);
        }
    }
}
