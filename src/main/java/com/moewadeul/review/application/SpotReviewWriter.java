package com.moewadeul.review.application;

import com.moewadeul.common.error.BusinessException;
import com.moewadeul.common.error.CommonErrorCode;
import com.moewadeul.common.time.KoreaTime;
import com.moewadeul.review.domain.ReviewErrorCode;
import com.moewadeul.review.domain.SpotReview;
import com.moewadeul.review.domain.SpotReviewRepository;
import com.moewadeul.review.infra.SpotReviewMapper;
import com.moewadeul.spot.application.ActiveSpotChecker;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
class SpotReviewWriter {

    private final ActiveSpotChecker activeSpotChecker;
    private final SpotReviewRepository spotReviewRepository;
    private final SpotReviewMapper spotReviewMapper;
    private final ReviewImageAttacher reviewImageAttacher;
    private final ReviewImageReader reviewImageReader;
    private final Clock clock;

    @Transactional
    public long write(long memberId, long spotId, SpotReviewWriteCommand command) {
        activeSpotChecker.requireActive(spotId);
        Instant now = clock.instant();
        requireNotFuture(command.visitedDate(), now);
        requireValidBody(command.rating(), command.content());
        SpotReview review =
                SpotReview.write(spotId, memberId, command.visitedDate(), command.rating(), command.content(), now);
        long reviewId = save(review).getId();
        reviewImageAttacher.replace(memberId, reviewId, command.imageIds());
        return reviewId;
    }

    @Transactional
    public SpotReviewItem revise(long memberId, long reviewId, SpotReviewReviseCommand command) {
        SpotReview review = loadOwnReview(memberId, reviewId);
        requireValidBody(command.rating(), command.content());
        review.revise(command.rating(), command.content(), clock.instant());
        if (command.imageIds() != null) {
            reviewImageAttacher.replace(memberId, reviewId, command.imageIds());
        }
        // 고친 내용을 MyBatis로 읽기 전에 DB에 반영한다.
        spotReviewRepository.flush();
        List<SpotReviewImage> images =
                reviewImageReader.readByReviewIds(List.of(reviewId)).getOrDefault(reviewId, List.of());
        return spotReviewMapper
                .selectById(reviewId)
                .map(row -> SpotReviewItem.from(row, images))
                .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));
    }

    @Transactional
    public void delete(long memberId, long reviewId) {
        spotReviewRepository.delete(loadOwnReview(memberId, reviewId));
    }

    @Transactional(readOnly = true)
    public void requireOwnReview(long memberId, long reviewId) {
        loadOwnReview(memberId, reviewId);
    }

    private SpotReview loadOwnReview(long memberId, long reviewId) {
        SpotReview review = spotReviewRepository
                .findById(reviewId)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));
        if (!review.isWrittenBy(memberId)) {
            throw new BusinessException(CommonErrorCode.ACCESS_DENIED);
        }
        return review;
    }

    private SpotReview save(SpotReview review) {
        try {
            return spotReviewRepository.saveAndFlush(review);
        } catch (DataIntegrityViolationException e) {
            throw new BusinessException(ReviewErrorCode.SPOT_REVIEW_DUPLICATED);
        }
    }

    private static void requireNotFuture(LocalDate visitedDate, Instant now) {
        if (visitedDate == null) {
            throw invalidInput("방문일을 입력해 주세요.");
        }
        if (visitedDate.isAfter(LocalDate.ofInstant(now, KoreaTime.ZONE))) {
            throw invalidInput("방문일은 오늘(한국 날짜)보다 뒤일 수 없어요.");
        }
    }

    private static void requireValidBody(int rating, String content) {
        if (rating < SpotReview.MIN_RATING || rating > SpotReview.MAX_RATING) {
            throw invalidInput("평점은 " + SpotReview.MIN_RATING + "~" + SpotReview.MAX_RATING + "이어야 해요.");
        }
        if (content == null || content.isBlank()) {
            throw invalidInput("후기 내용을 입력해 주세요.");
        }
        if (content.length() > SpotReview.CONTENT_MAX_LENGTH) {
            throw invalidInput("후기 내용은 " + SpotReview.CONTENT_MAX_LENGTH + "자 이하여야 해요.");
        }
    }

    private static BusinessException invalidInput(String message) {
        return new BusinessException(CommonErrorCode.INVALID_INPUT, message);
    }
}
