package com.moewadeul.review.application;

import com.moewadeul.common.transaction.OutsideTransaction;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class SpotReviewCommandService {

    private static final String STORAGE_CHECK = "이미지가 저장소에 있는지 묻는 동안 DB 커넥션을 쥐면 안 된다.";

    private final SpotReviewWriter spotReviewWriter;
    private final ReviewImageAttachmentPrecheck reviewImageAttachmentPrecheck;

    public long write(long memberId, long spotId, SpotReviewWriteCommand command) {
        OutsideTransaction.require(STORAGE_CHECK);
        reviewImageAttachmentPrecheck.verify(memberId, null, command.imageIds());
        return spotReviewWriter.write(memberId, spotId, command);
    }

    public SpotReviewItem revise(long memberId, long reviewId, SpotReviewReviseCommand command) {
        OutsideTransaction.require(STORAGE_CHECK);
        if (command.imageIds() != null) {
            spotReviewWriter.requireOwnReview(memberId, reviewId);
            reviewImageAttachmentPrecheck.verify(memberId, reviewId, command.imageIds());
        }
        return spotReviewWriter.revise(memberId, reviewId, command);
    }

    public void delete(long memberId, long reviewId) {
        spotReviewWriter.delete(memberId, reviewId);
    }
}
