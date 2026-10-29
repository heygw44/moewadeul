package com.moewadeul.review.application;

import static com.moewadeul.member.domain.MemberBuilder.aMember;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.moewadeul.common.error.CommonErrorCode;
import com.moewadeul.common.error.InvalidFieldException;
import com.moewadeul.common.error.RateLimitedException;
import com.moewadeul.common.image.ImageUpload;
import com.moewadeul.common.testsupport.IntegrationTest;
import com.moewadeul.common.testsupport.MutableClock;
import com.moewadeul.common.testsupport.TestImageStorage;
import com.moewadeul.member.infra.MemberJpaRepository;
import com.moewadeul.review.domain.ReviewImage;
import com.moewadeul.spot.application.detail.SpotDetail;
import com.moewadeul.spot.application.detail.SpotDetailQueryService;
import com.moewadeul.spot.application.detail.SpotRecentReview;
import com.moewadeul.spot.domain.GeoPoint;
import com.moewadeul.spot.domain.ParkAreaJudgement;
import com.moewadeul.spot.domain.Spot;
import com.moewadeul.spot.infra.SpotJpaRepository;
import java.time.Duration;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

@IntegrationTest
class ReviewImageIntegrationTest {

    private static final LocalDate YESTERDAY = LocalDate.of(2026, 10, 4);
    private static final long SIZE = 1024;
    private static final String UNUSABLE = "붙일 수 없는 이미지가 있어요.";

    @Autowired
    private ReviewImageUploadService uploadService;

    @Autowired
    private SpotReviewCommandService commandService;

    @Autowired
    private SpotReviewQueryService queryService;

    @Autowired
    private ReviewImageCleanupService cleanupService;

    @Autowired
    private SpotDetailQueryService spotDetailQueryService;

    @Autowired
    private SpotJpaRepository spotRepository;

    @Autowired
    private MemberJpaRepository memberRepository;

    @Autowired
    private TestImageStorage storage;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private MutableClock clock;

    private long memberId;
    private long spotId;

    @BeforeEach
    void setUp() {
        memberId = saveMember();
        spotId = saveSpot();
    }

    @Test
    @DisplayName("[F-24][RI-01] 업로드 URL을 발급하면 후기에 붙지 않은 행을 만들고, 객체 키는 review/회원 ID/로 시작한다")
    void issueCreatesUnattachedRow() {
        // when
        ImageUpload upload = uploadService.issue(memberId, "image/webp", SIZE);

        // then
        assertThat(keyOf(upload.imageId()))
                .startsWith("review/" + memberId + "/")
                .endsWith(".webp");
        assertThat(jdbc.queryForObject("SELECT review_id FROM review_image WHERE id = ?", Long.class, upload.imageId()))
                .isNull();
        assertThat(upload.headers()).containsEntry("Content-Type", "image/webp");
    }

    @Test
    @DisplayName("[F-24][RI-01] 허용하지 않는 형식이나 5MB를 넘는 크기는 INVALID_INPUT으로 거부한다")
    void issueRejectsTypeAndSize() {
        assertInvalid(() -> uploadService.issue(memberId, "image/gif", SIZE), "contentType", "허용하지 않는 이미지 형식이에요.");
        assertInvalid(
                () -> uploadService.issue(memberId, "image/png", 5L * 1024 * 1024 + 1),
                "sizeBytes",
                "이미지 크기는 1바이트 이상 5MB 이하여야 해요.");
    }

    @Test
    @DisplayName(
            "[F-24][RI-01] 24시간 안에 발급해 후기에 붙지 않은 이미지가 9장이면 다음 발급은 429 IMAGE_UPLOAD_LIMITED이고 행을 만들지 않으며, 가장 오래된 이미지가 24시간을 넘기면 다시 발급한다")
    void issueLimitsUnattachedUploads() {
        // given
        uploadService.issue(memberId, "image/jpeg", SIZE);
        clock.advance(Duration.ofHours(1));
        for (int i = 1; i < ReviewImage.MAX_UNATTACHED_PER_MEMBER; i++) {
            uploadService.issue(memberId, "image/jpeg", SIZE);
        }

        // when
        ThrowingCallable overLimit = () -> uploadService.issue(memberId, "image/jpeg", SIZE);

        // then
        assertThatThrownBy(overLimit).isInstanceOfSatisfying(RateLimitedException.class, e -> {
            assertThat(e.getErrorCode()).isEqualTo(CommonErrorCode.IMAGE_UPLOAD_LIMITED);
            assertThat(e.retryAfter()).isEqualTo(Duration.ofHours(23));
        });
        assertThat(imageRowCount()).isEqualTo(ReviewImage.MAX_UNATTACHED_PER_MEMBER);
        clock.advance(Duration.ofHours(23));
        uploadService.issue(memberId, "image/jpeg", SIZE);
        assertThat(imageRowCount()).isEqualTo(ReviewImage.MAX_UNATTACHED_PER_MEMBER + 1);
    }

    @Test
    @DisplayName("[F-24][RI-02] 올린 이미지를 붙여 후기를 쓰면 후기 목록과 장소 상세 최근 후기에 이미지가 순서대로 보인다")
    void writtenImagesAppearInListAndDetail() {
        // given
        long first = uploaded(memberId);
        long second = uploaded(memberId);

        // when
        long reviewId = commandService.write(memberId, spotId, write(List.of(second, first)));

        // then
        SpotReviewItem item = queryService.list(spotId, 0, 20).content().get(0);
        assertThat(item.reviewId()).isEqualTo(reviewId);
        assertThat(item.images()).extracting(SpotReviewImage::imageId).containsExactly(second, first);
        assertThat(item.images().get(0).url()).contains(keyOf(second));
        SpotDetail detail = spotDetailQueryService.findDetail(spotId);
        assertThat(detail.recentReviews().get(0).images())
                .extracting(SpotRecentReview.Image::imageId)
                .containsExactly(second, first);
    }

    @Test
    @DisplayName("[F-24][RI-02] 이미지 4장, 같은 ID 두 번, 다른 회원의 이미지, 저장소에 없는 이미지, 빈 ID는 imageIds 오류로 거부하고 후기를 저장하지 않는다")
    void rejectsUnusableImages() {
        // given
        long otherMember = saveMember();
        long others = uploaded(otherMember);
        long notUploaded = uploadService.issue(memberId, "image/png", SIZE).imageId();
        List<Long> four = List.of(uploaded(memberId), uploaded(memberId), uploaded(memberId), uploaded(memberId));
        long mine = uploaded(memberId);

        // when, then
        assertInvalid(() -> commandService.write(memberId, spotId, write(four)), "imageIds", "이미지는 3장까지 붙일 수 있어요.");
        assertInvalid(
                () -> commandService.write(memberId, spotId, write(List.of(mine, mine))),
                "imageIds",
                "같은 이미지를 두 번 붙일 수 없어요.");
        assertInvalid(() -> commandService.write(memberId, spotId, write(List.of(others))), "imageIds", UNUSABLE);
        assertInvalid(() -> commandService.write(memberId, spotId, write(List.of(notUploaded))), "imageIds", UNUSABLE);
        assertInvalid(
                () -> commandService.write(memberId, spotId, write(Arrays.asList(mine, null))), "imageIds", UNUSABLE);
        assertInvalid(
                () -> commandService.write(memberId, spotId, write(Arrays.asList(null, null))), "imageIds", UNUSABLE);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM spot_review", Integer.class))
                .isZero();
    }

    @Test
    @DisplayName("[F-24][RI-02] 다른 후기에 이미 붙은 이미지는 새 후기에 붙일 수 없다")
    void rejectsImageAttachedToAnotherReview() {
        // given
        long image = uploaded(memberId);
        commandService.write(memberId, spotId, write(List.of(image)));

        // when, then
        assertInvalid(
                () -> commandService.write(
                        memberId,
                        spotId,
                        new SpotReviewWriteCommand(YESTERDAY.minusDays(1), 4, "또 갔다", List.of(image))),
                "imageIds",
                UNUSABLE);
    }

    @Test
    @DisplayName("[F-24][RI-03] 수정에서 imageIds를 보내면 목록을 통째로 바꾸고 빠진 이미지는 떨어지며, 보내지 않으면 그대로 둔다")
    void reviseReplacesOrKeepsImages() {
        // given
        long kept = uploaded(memberId);
        long removed = uploaded(memberId);
        long added = uploaded(memberId);
        long reviewId = commandService.write(memberId, spotId, write(List.of(kept, removed)));

        // when
        SpotReviewItem replaced =
                commandService.revise(memberId, reviewId, new SpotReviewReviseCommand(3, "고쳤다", List.of(added, kept)));
        SpotReviewItem untouched = commandService.revise(memberId, reviewId, new SpotReviewReviseCommand(2, "또 고쳤다"));
        SpotReviewItem cleared =
                commandService.revise(memberId, reviewId, new SpotReviewReviseCommand(2, "비웠다", List.of()));

        // then
        assertThat(replaced.images()).extracting(SpotReviewImage::imageId).containsExactly(added, kept);
        assertThat(reviewIdOf(removed)).isNull();
        assertThat(untouched.images()).extracting(SpotReviewImage::imageId).containsExactly(added, kept);
        assertThat(cleared.images()).isEmpty();
        assertThat(reviewIdOf(kept)).isNull();
    }

    @Test
    @DisplayName("[F-24][RI-04] 후기를 지우면 이미지는 떨어지고, 24시간이 지나면 정리 작업이 행과 저장소 객체를 지운다")
    void deletedReviewImagesAreCleanedUp() {
        // given
        long image = uploaded(memberId);
        long reviewId = commandService.write(memberId, spotId, write(List.of(image)));
        String key = keyOf(image);

        // when
        commandService.delete(memberId, reviewId);
        Map<String, Object> detached =
                jdbc.queryForMap("SELECT review_id, display_order FROM review_image WHERE id = ?", image);
        int beforeRetention = cleanupService.deleteExpiredRowsThenObjects().deletedRows();
        clock.advance(ReviewImageCleanupService.RETENTION.plus(Duration.ofSeconds(1)));
        ReviewImageCleanupService.Result result = cleanupService.deleteExpiredRowsThenObjects();

        // then
        assertThat(detached).containsEntry("review_id", null).containsEntry("display_order", null);
        assertThat(beforeRetention).isZero();
        assertThat(result.deletedRows()).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM review_image WHERE id = ?", Integer.class, image))
                .isZero();
        assertThat(storage.deletedKeys()).containsExactly(key);
    }

    @Test
    @DisplayName("[F-24][RI-04] 오래됐어도 후기에 붙은 이미지는 정리 작업이 지우지 않는다")
    void cleanupKeepsAttachedImages() {
        // given
        long attached = uploaded(memberId);
        long unattached = uploaded(memberId);
        commandService.write(memberId, spotId, write(List.of(attached)));
        clock.advance(Duration.ofDays(3));

        // when
        ReviewImageCleanupService.Result result = cleanupService.deleteExpiredRowsThenObjects();

        // then
        assertThat(result.deletedRows()).isEqualTo(1);
        assertThat(storage.hasObject(keyOf(attached))).isTrue();
        assertThat(storage.deletedKeys()).doesNotContain(keyOf(attached));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM review_image WHERE id = ?", Integer.class, unattached))
                .isZero();
    }

    private static SpotReviewWriteCommand write(List<Long> imageIds) {
        return new SpotReviewWriteCommand(YESTERDAY, 4, "물이 가까웠다", imageIds);
    }

    private long uploaded(long ownerId) {
        long imageId = uploadService.issue(ownerId, "image/jpeg", SIZE).imageId();
        storage.markUploaded(keyOf(imageId), SIZE);
        return imageId;
    }

    private String keyOf(long imageId) {
        return jdbc.queryForObject("SELECT object_key FROM review_image WHERE id = ?", String.class, imageId);
    }

    private Long reviewIdOf(long imageId) {
        return jdbc.queryForObject("SELECT review_id FROM review_image WHERE id = ?", Long.class, imageId);
    }

    private int imageRowCount() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM review_image", Integer.class);
    }

    private long saveSpot() {
        Spot spot = Spot.bakji(
                "능선 끝 평지",
                new GeoPoint(37.25, 127.25),
                ParkAreaJudgement.outside(MutableClock.DEFAULT_INSTANT),
                MutableClock.DEFAULT_INSTANT);
        return spotRepository.save(spot).getId();
    }

    private long saveMember() {
        return memberRepository.saveAndFlush(aMember().build()).getId();
    }

    private static void assertInvalid(ThrowingCallable action, String field, String reason) {
        assertThatThrownBy(action).isInstanceOfSatisfying(InvalidFieldException.class, e -> {
            assertThat(e.getField()).isEqualTo(field);
            assertThat(e.getMessage()).isEqualTo(reason);
        });
    }
}
