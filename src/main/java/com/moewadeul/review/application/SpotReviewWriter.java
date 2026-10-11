package com.moewadeul.review.application;

import com.moewadeul.common.error.BusinessException;
import com.moewadeul.common.error.CommonErrorCode;
import com.moewadeul.review.domain.ReviewErrorCode;
import com.moewadeul.review.domain.SpotReview;
import com.moewadeul.review.domain.SpotReviewRepository;
import com.moewadeul.review.infra.SpotReviewMapper;
import com.moewadeul.spot.application.ActiveSpotChecker;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 후기를 저장하고, 고치고, 지우는 트랜잭션을 가진다. {@link SpotReviewCommandService}가 이미지를 저장소까지 확인한 뒤에 부른다.
 *
 * <p>후기를 쓸 수 있는 장소는 지도에 보이는(ACTIVE) 장소뿐이다. 장소가 없거나 ACTIVE가 아니면 NOT_FOUND로 거부한다. 반대로 고치거나 지울 때는
 * 장소 상태를 보지 않는다. 장소가 숨겨진 뒤에도 작성자는 자기 후기를 고치거나 지울 수 있어야 하기 때문이다.
 *
 * <p>한 회원은 같은 장소에 방문일마다 후기를 하나만 쓴다. 조회로 미리 거르지 않고, 저장할 때 DB가 던지는 유니크 제약 위반을
 * SPOT_REVIEW_DUPLICATED로 바꾼다. 같은 회원의 요청 두 개가 동시에 들어와도 DB가 하나만 받기 때문이다. 평균 평점과 후기 수는 저장하지 않고
 * 장소 상세가 조회할 때 계산하므로, 후기를 쓰고 고치고 지울 때 갱신할 집계가 없고 락도 필요 없다.
 */
@Component
@RequiredArgsConstructor
class SpotReviewWriter {

    private static final ZoneId KOREA = ZoneId.of("Asia/Seoul");

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

    // 후기 행을 지우면 FK가 붙은 이미지의 후기 ID를 NULL로 바꾸므로, 이미지는 붙지 않은 업로드가 되어 정리 작업이 지운다.
    @Transactional
    public void delete(long memberId, long reviewId) {
        spotReviewRepository.delete(loadOwnReview(memberId, reviewId));
    }

    /** 호출하면 reviewId인 후기가 있고 memberId가 쓴 후기인지 확인한다. 없으면 NOT_FOUND로, 남의 후기면 ACCESS_DENIED로 거부한다. */
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

    // 방문일은 한국 날짜로 받으므로, 오늘도 한국 날짜로 계산해서 비교한다. 오늘 방문한 장소의 후기는 쓸 수 있다.
    private static void requireNotFuture(LocalDate visitedDate, Instant now) {
        if (visitedDate == null) {
            throw invalidInput("방문일을 입력해야 합니다.");
        }
        if (visitedDate.isAfter(LocalDate.ofInstant(now, KOREA))) {
            throw invalidInput("방문일은 오늘(한국 날짜)보다 뒤일 수 없습니다.");
        }
    }

    private static void requireValidBody(int rating, String content) {
        if (rating < SpotReview.MIN_RATING || rating > SpotReview.MAX_RATING) {
            throw invalidInput("평점은 " + SpotReview.MIN_RATING + "~" + SpotReview.MAX_RATING + "이어야 합니다.");
        }
        if (content == null || content.isBlank()) {
            throw invalidInput("후기 내용을 입력해야 합니다.");
        }
        if (content.length() > SpotReview.CONTENT_MAX_LENGTH) {
            throw invalidInput("후기 내용은 " + SpotReview.CONTENT_MAX_LENGTH + "자 이하여야 합니다.");
        }
    }

    private static BusinessException invalidInput(String message) {
        return new BusinessException(CommonErrorCode.INVALID_INPUT, message);
    }
}
