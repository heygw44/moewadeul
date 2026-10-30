package com.moewadeul.community.application.post;

import static org.assertj.core.api.Assertions.assertThat;

import com.moewadeul.common.image.application.ImageUploadCommand;
import com.moewadeul.common.testsupport.IntegrationTest;
import com.moewadeul.common.testsupport.MemberFixture;
import com.moewadeul.common.testsupport.MutableClock;
import com.moewadeul.common.testsupport.TestImageStorage;
import com.moewadeul.community.application.image.CommunityImageCleanupService;
import com.moewadeul.community.application.image.CommunityImageUploadService;
import com.moewadeul.community.application.moderation.CommunityModerationService;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

@IntegrationTest
class CommunityPostDeleteImageIntegrationTest {

    private static final long IMAGE_SIZE = 1000L;

    @Autowired
    private MemberFixture memberFixture;

    @Autowired
    private CommunityPostCommandService postCommandService;

    @Autowired
    private CommunityModerationService moderationService;

    @Autowired
    private CommunityImageUploadService uploadService;

    @Autowired
    private CommunityImageCleanupService cleanupService;

    @Autowired
    private TestImageStorage storage;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private MutableClock clock;

    private long authorId;

    @BeforeEach
    void setUp() {
        authorId = memberFixture.save().getId();
    }

    @Test
    @DisplayName("[F-29][CM-06] 작성자가 이미지가 붙은 글을 지우면 이미지가 글에서 떨어지고, 24시간이 지난 뒤 정리 작업이 지운다")
    void authorDeleteDetachesImagesForCleanup() {
        // given
        List<Long> imageIds = uploadedImages(2);
        long postId = postCommandService.write(authorId, new CommunityPostWriteCommand("제목", "본문", null, imageIds));
        assertThat(attachedImageIds(postId)).containsExactlyElementsOf(imageIds);

        // when
        postCommandService.delete(authorId, postId);

        // then
        assertThat(attachedImageIds(postId)).isEmpty();
        assertThat(unattachedImageIds()).containsExactlyInAnyOrderElementsOf(imageIds);

        clock.advance(CommunityImageCleanupService.RETENTION.plus(Duration.ofSeconds(1)));
        cleanupService.deleteExpiredRowsThenObjects();
        assertThat(unattachedImageIds()).isEmpty();
    }

    @Test
    @DisplayName("[F-29][CM-08] 관리자가 이미지가 붙은 글을 숨겨도 이미지는 글에 붙은 채로 남는다")
    void adminHideKeepsImagesAttached() {
        // given
        List<Long> imageIds = uploadedImages(2);
        long postId = postCommandService.write(authorId, new CommunityPostWriteCommand("제목", "본문", null, imageIds));
        long adminId = memberFixture.save().getId();

        // when
        moderationService.hidePost(postId, adminId);

        // then
        assertThat(attachedImageIds(postId)).containsExactlyElementsOf(imageIds);
    }

    private List<Long> attachedImageIds(long postId) {
        return jdbc.queryForList(
                "SELECT id FROM community_image WHERE post_id = ? ORDER BY display_order", Long.class, postId);
    }

    private List<Long> unattachedImageIds() {
        return jdbc.queryForList(
                "SELECT id FROM community_image WHERE member_id = ? AND post_id IS NULL", Long.class, authorId);
    }

    private List<Long> uploadedImages(int count) {
        List<Long> ids = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            long imageId = uploadService
                    .issue(authorId, new ImageUploadCommand("image/jpeg", IMAGE_SIZE, null))
                    .imageId();
            storage.markUploaded(
                    jdbc.queryForObject("SELECT object_key FROM community_image WHERE id = ?", String.class, imageId),
                    IMAGE_SIZE);
            ids.add(imageId);
        }
        return ids;
    }
}
