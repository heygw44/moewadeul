package com.moewadeul.review.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class SpotReviewCommandService {

    private final SpotReviewWriter spotReviewWriter;
    private final ReviewImageAttachments reviewImageAttachments;

    public long write(long memberId, long spotId, SpotReviewWriteCommand command) {
        reviewImageAttachments.precheck(memberId, null, command.imageIds());
        return spotReviewWriter.write(memberId, spotId, command);
    }

    public SpotReviewItem revise(long memberId, long reviewId, SpotReviewReviseCommand command) {
        if (command.imageIds() != null) {
            spotReviewWriter.requireOwnReview(memberId, reviewId);
            reviewImageAttachments.precheck(memberId, reviewId, command.imageIds());
        }
        return spotReviewWriter.revise(memberId, reviewId, command);
    }

    public void delete(long memberId, long reviewId) {
        spotReviewWriter.delete(memberId, reviewId);
    }
}
