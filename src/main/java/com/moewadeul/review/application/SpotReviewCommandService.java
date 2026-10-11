package com.moewadeul.review.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 회원이 장소 후기를 쓰고, 자기 후기를 고치거나 지운다.
 *
 * <p>이 서비스는 트랜잭션을 열지 않는다. 후기에 이미지를 붙일 때 저장소에 객체가 있는지 묻는 호출은 DB 트랜잭션 밖에서 해야 하기 때문이다. 이미지를
 * 확인한 뒤에 트랜잭션을 가진 {@link SpotReviewWriter}를 불러 후기를 저장한다. 후기를 저장하는 규칙(장소, 방문일, 평점, 내용, 작성자 검사)은
 * 그쪽에 있다.
 */
@Service
@RequiredArgsConstructor
public class SpotReviewCommandService {

    private final SpotReviewWriter spotReviewWriter;
    private final ReviewImageAttachmentChecker reviewImageAttachmentChecker;

    /**
     * 호출하면 memberId인 회원의 후기를 저장하고 후기 ID를 돌려준다.
     *
     * <p>방문일이 오늘(한국 날짜)보다 뒤이거나, 평점이 1~5가 아니거나, 내용이 공백뿐이거나 2,000자를 넘으면 INVALID_INPUT으로 거부한다.
     * 같은 장소에 같은 방문일로 쓴 후기가 이미 있으면 SPOT_REVIEW_DUPLICATED로 거부한다. 장소가 없거나 ACTIVE가 아니면 NOT_FOUND로 거부한다.
     * 붙일 이미지가 3장을 넘거나 겹치거나, 회원이 올리지 않았거나, 이미 다른 후기에 붙었거나, 저장소에 없거나 크기가 다르면 {@code imageIds} 필드의
     * INVALID_INPUT으로 거부한다.
     */
    public long write(long memberId, long spotId, SpotReviewWriteCommand command) {
        reviewImageAttachmentChecker.verify(memberId, null, command.imageIds());
        return spotReviewWriter.write(memberId, spotId, command);
    }

    /**
     * 호출하면 작성자 memberId가 reviewId인 후기의 평점과 내용을 고치고, 고친 뒤의 후기를 돌려준다. 방문일은 그대로다. imageIds를 보내면 후기의
     * 이미지를 그 순서대로 통째로 바꾸고, 이미 이 후기에 붙은 이미지는 다시 보내도 된다.
     *
     * <p>후기가 없으면 NOT_FOUND로, 다른 회원이 쓴 후기이면 ACCESS_DENIED로 거부한다. 평점이나 내용, 이미지가 규칙을 어기면 INVALID_INPUT으로
     * 거부한다.
     */
    public SpotReviewItem revise(long memberId, long reviewId, SpotReviewReviseCommand command) {
        if (command.imageIds() != null) {
            // 후기가 없거나 남의 후기이면 이미지 검사보다 먼저 404나 403으로 거부한다.
            spotReviewWriter.requireOwnReview(memberId, reviewId);
            reviewImageAttachmentChecker.verify(memberId, reviewId, command.imageIds());
        }
        return spotReviewWriter.revise(memberId, reviewId, command);
    }

    /**
     * 호출하면 작성자 memberId의 후기를 지운다. 붙은 이미지는 후기에서 떨어지고 정리 작업이 지운다. 후기가 없으면 NOT_FOUND로, 다른 회원이 쓴
     * 후기이면 ACCESS_DENIED로 거부한다.
     */
    public void delete(long memberId, long reviewId) {
        spotReviewWriter.delete(memberId, reviewId);
    }
}
