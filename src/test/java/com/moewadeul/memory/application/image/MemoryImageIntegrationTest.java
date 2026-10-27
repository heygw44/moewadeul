package com.moewadeul.memory.application.image;

import static com.moewadeul.member.domain.MemberBuilder.aMember;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.moewadeul.common.error.InvalidFieldException;
import com.moewadeul.common.testsupport.IntegrationTest;
import com.moewadeul.common.testsupport.MutableClock;
import com.moewadeul.common.testsupport.TestImageStorage;
import com.moewadeul.common.web.PatchField;
import com.moewadeul.member.infra.MemberJpaRepository;
import com.moewadeul.memory.application.MemoryCommandService;
import com.moewadeul.memory.application.MemoryPhoto;
import com.moewadeul.memory.application.MemoryReviseCommand;
import com.moewadeul.memory.application.MemoryView;
import com.moewadeul.memory.application.MemoryWriteCommand;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

@IntegrationTest
class MemoryImageIntegrationTest {

    private static final LocalDate YESTERDAY = LocalDate.of(2026, 10, 4);
    private static final long SIZE = 1024;
    private static final long THUMBNAIL_SIZE = 64;

    @Autowired
    private MemoryImageUploadService uploadService;

    @Autowired
    private MemoryCommandService commandService;

    @Autowired
    private MemoryImageCleanupService cleanupService;

    @Autowired
    private MemberJpaRepository memberRepository;

    @Autowired
    private TestImageStorage storage;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private MutableClock clock;

    private long memberId;

    @BeforeEach
    void setUp() {
        memberId = saveMember();
    }

    @Test
    @DisplayName("[F-33][MM-05] 업로드 URL을 발급하면 기록에 붙지 않은 행을 만들고, 객체 키는 memory/회원 ID/로 시작한다")
    void issueCreatesUnattachedRow() {
        // when
        MemoryImageUpload upload = uploadService.issue(memberId, command("image/webp", SIZE));

        // then
        assertThat(keyOf(upload.imageId()))
                .startsWith("memory/" + memberId + "/")
                .endsWith(".webp");
        assertThat(memoryIdOf(upload.imageId())).isNull();
        assertThat(upload.headers()).containsEntry("Content-Type", "image/webp");
        assertThat(upload.thumbnailUpload()).isNull();
        assertThat(thumbnailKeyOf(upload.imageId())).isNull();
    }

    @Test
    @DisplayName("[F-33][MM-05] 썸네일 크기를 보내면 원본과 같은 UUID에 _thumb.jpg를 붙인 키로 JPEG 썸네일 업로드 URL도 발급한다")
    void issueWithThumbnailCreatesThumbnailKey() {
        // when
        MemoryImageUpload upload = uploadService.issue(memberId, withThumbnail("image/png", SIZE, THUMBNAIL_SIZE));

        // then
        String key = keyOf(upload.imageId());
        String thumbnailKey = thumbnailKeyOf(upload.imageId());
        assertThat(thumbnailKey).isEqualTo(key.substring(0, key.length() - ".png".length()) + "_thumb.jpg");
        assertThat(upload.thumbnailUpload().url()).contains(thumbnailKey);
        assertThat(upload.thumbnailUpload().headers())
                .containsEntry("Content-Type", "image/jpeg")
                .containsEntry("Content-Length", String.valueOf(THUMBNAIL_SIZE));
        assertThat(jdbc.queryForObject(
                        "SELECT thumbnail_size_bytes FROM memory_image WHERE id = ?", Long.class, upload.imageId()))
                .isEqualTo(THUMBNAIL_SIZE);
    }

    @Test
    @DisplayName("[F-33][MM-05] 썸네일 크기가 0이거나 256KB를 넘으면 thumbnailSizeBytes 필드의 INVALID_INPUT으로 거부한다")
    void issueRejectsThumbnailSize() {
        assertInvalid(() -> uploadService.issue(memberId, withThumbnail("image/png", SIZE, 0)), "thumbnailSizeBytes");
        assertInvalid(
                () -> uploadService.issue(memberId, withThumbnail("image/png", SIZE, 256L * 1024 + 1)),
                "thumbnailSizeBytes");
    }

    @Test
    @DisplayName("[F-33][MM-05] 썸네일 URL도 발급한 사진은 썸네일이 올라오지 않았거나 크기가 다르면 거부하고, 원본과 썸네일이 모두 올라오면 붙인다")
    void attachRequiresThumbnailWhenIssued() {
        // given
        long missing = issueWithThumbnail();
        storage.markUploaded(keyOf(missing), SIZE);
        long wrongSize = issueWithThumbnail();
        storage.markUploaded(keyOf(wrongSize), SIZE);
        storage.markUploaded(thumbnailKeyOf(wrongSize), THUMBNAIL_SIZE + 1);
        long complete = issueWithThumbnail();
        storage.markUploaded(keyOf(complete), SIZE);
        storage.markUploaded(thumbnailKeyOf(complete), THUMBNAIL_SIZE);

        // when
        long memoryId = commandService.write(memberId, write(List.of(complete)));

        // then
        assertInvalid(() -> commandService.write(memberId, write(List.of(missing))), "imageIds");
        assertInvalid(() -> commandService.write(memberId, write(List.of(wrongSize))), "imageIds");
        assertThat(memoryIdOf(complete)).isEqualTo(memoryId);
    }

    @Test
    @DisplayName("[F-33][MM-05] 허용하지 않는 형식이나 5MB를 넘는 크기는 INVALID_INPUT으로 거부한다")
    void issueRejectsTypeAndSize() {
        assertInvalid(() -> uploadService.issue(memberId, command("image/gif", SIZE)), "contentType");
        assertInvalid(() -> uploadService.issue(memberId, command("image/png", 5L * 1024 * 1024 + 1)), "sizeBytes");
    }

    @Test
    @DisplayName("[F-33][MM-05] 사진 10장은 붙이고, 11장, 같은 ID 두 번, 저장소에 없는 사진, 다른 기록에 붙은 사진은 imageIds 오류로 거부한다")
    void attachRules() {
        // given
        List<Long> ten = uploadedImages(10);
        List<Long> eleven = uploadedImages(11);
        long mine = uploaded(memberId);
        long notUploaded =
                uploadService.issue(memberId, command("image/png", SIZE)).imageId();
        long attached = uploaded(memberId);
        commandService.write(memberId, write(List.of(attached)));

        // when
        long memoryId = commandService.write(memberId, write(ten));

        // then
        assertThat(jdbc.queryForObject(
                        "SELECT COUNT(*) FROM memory_image WHERE memory_id = ?", Integer.class, memoryId))
                .isEqualTo(10);
        assertInvalid(() -> commandService.write(memberId, write(eleven)), "imageIds");
        assertInvalid(() -> commandService.write(memberId, write(List.of(mine, mine))), "imageIds");
        assertInvalid(() -> commandService.write(memberId, write(List.of(notUploaded))), "imageIds");
        assertInvalid(() -> commandService.write(memberId, write(List.of(attached))), "imageIds");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM memory", Integer.class))
                .isEqualTo(2);
    }

    @Test
    @DisplayName("[F-33][MM-05] 수정에서 imageIds를 보내면 목록을 통째로 바꾸고 빠진 사진은 떨어지며, 보내지 않으면 그대로 둔다")
    void reviseReplacesOrKeepsImages() {
        // given
        long kept = uploaded(memberId);
        long removed = uploaded(memberId);
        long added = uploaded(memberId);
        long memoryId = commandService.write(memberId, write(List.of(kept, removed)));

        // when
        MemoryView replaced = commandService.revise(memberId, memoryId, imagesOnly(List.of(added, kept)));
        MemoryView untouched = commandService.revise(memberId, memoryId, nightsOnly(2));
        MemoryView cleared = commandService.revise(memberId, memoryId, imagesOnly(List.of()));

        // then
        assertThat(replaced.images()).extracting(MemoryPhoto::imageId).containsExactly(added, kept);
        assertThat(memoryIdOf(removed)).isNull();
        assertThat(untouched.images()).extracting(MemoryPhoto::imageId).containsExactly(added, kept);
        assertThat(untouched.nights()).isEqualTo(2);
        assertThat(cleared.images()).isEmpty();
        assertThat(memoryIdOf(kept)).isNull();
    }

    @Test
    @DisplayName("[F-33][MM-05] 기록을 지우면 사진은 떨어지고, 24시간이 지나면 정리 작업이 행과 저장소 객체를 지운다")
    void deletedMemoryImagesAreCleanedUp() {
        // given
        long image = uploaded(memberId);
        long memoryId = commandService.write(memberId, write(List.of(image)));
        String key = keyOf(image);

        // when
        commandService.delete(memberId, memoryId);
        int beforeRetention = cleanupService.deleteUnattached().deletedRows();
        clock.advance(MemoryImageCleanupService.RETENTION.plus(Duration.ofSeconds(1)));
        MemoryImageCleanupService.Result result = cleanupService.deleteUnattached();

        // then
        assertThat(beforeRetention).isZero();
        assertThat(result.deletedRows()).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM memory_image WHERE id = ?", Integer.class, image))
                .isZero();
        assertThat(storage.deletedKeys()).containsExactly(key);
    }

    @Test
    @DisplayName("[F-33][MM-05] 정리 작업은 썸네일이 있는 사진의 원본과 썸네일 객체를 모두 지운다")
    void cleanupDeletesOriginalAndThumbnail() {
        // given
        long image = issueWithThumbnail();
        String key = keyOf(image);
        String thumbnailKey = thumbnailKeyOf(image);
        storage.markUploaded(key, SIZE);
        storage.markUploaded(thumbnailKey, THUMBNAIL_SIZE);
        clock.advance(MemoryImageCleanupService.RETENTION.plus(Duration.ofSeconds(1)));

        // when
        MemoryImageCleanupService.Result result = cleanupService.deleteUnattached();

        // then
        assertThat(result.deletedRows()).isEqualTo(1);
        assertThat(result.storageFailures()).isZero();
        assertThat(storage.deletedKeys()).containsExactly(key, thumbnailKey);
    }

    @Test
    @DisplayName("[F-33][MM-05] 정리 작업은 원본 객체 삭제가 실패해도 썸네일 객체를 지우고 실패를 한 번 센다")
    void cleanupDeletesThumbnailEvenWhenOriginalDeleteFails() {
        // given
        long image = issueWithThumbnail();
        String key = keyOf(image);
        String thumbnailKey = thumbnailKeyOf(image);
        storage.failDeleteOf(key);
        clock.advance(MemoryImageCleanupService.RETENTION.plus(Duration.ofSeconds(1)));

        // when
        MemoryImageCleanupService.Result result = cleanupService.deleteUnattached();

        // then
        assertThat(result.deletedRows()).isEqualTo(1);
        assertThat(result.storageFailures()).isEqualTo(1);
        assertThat(storage.deletedKeys()).containsExactly(thumbnailKey);
    }

    @Test
    @DisplayName("[F-33][MM-05] 오래됐어도 기록에 붙은 사진은 정리 작업이 지우지 않는다")
    void cleanupKeepsAttachedImages() {
        // given
        long attached = uploaded(memberId);
        long unattached = uploaded(memberId);
        commandService.write(memberId, write(List.of(attached)));
        clock.advance(Duration.ofDays(3));

        // when
        MemoryImageCleanupService.Result result = cleanupService.deleteUnattached();

        // then
        assertThat(result.deletedRows()).isEqualTo(1);
        assertThat(storage.hasObject(keyOf(attached))).isTrue();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM memory_image WHERE id = ?", Integer.class, unattached))
                .isZero();
    }

    private static MemoryImageUploadCommand command(String contentType, long sizeBytes) {
        return new MemoryImageUploadCommand(contentType, sizeBytes, null);
    }

    private static MemoryImageUploadCommand withThumbnail(String contentType, long sizeBytes, long thumbnailSizeBytes) {
        return new MemoryImageUploadCommand(contentType, sizeBytes, thumbnailSizeBytes);
    }

    private long issueWithThumbnail() {
        return uploadService
                .issue(memberId, withThumbnail("image/jpeg", SIZE, THUMBNAIL_SIZE))
                .imageId();
    }

    private static MemoryWriteCommand write(List<Long> imageIds) {
        return new MemoryWriteCommand(null, 37.25, 127.25, YESTERDAY, 1, "물가 옆 평지", null, imageIds);
    }

    private static MemoryReviseCommand imagesOnly(List<Long> imageIds) {
        return revise(PatchField.absent(), PatchField.of(imageIds));
    }

    private static MemoryReviseCommand nightsOnly(int nights) {
        return revise(PatchField.of(nights), PatchField.absent());
    }

    private static MemoryReviseCommand revise(PatchField<Integer> nights, PatchField<List<Long>> imageIds) {
        return new MemoryReviseCommand(
                PatchField.absent(),
                PatchField.absent(),
                PatchField.absent(),
                PatchField.absent(),
                nights,
                PatchField.absent(),
                PatchField.absent(),
                imageIds);
    }

    private List<Long> uploadedImages(int count) {
        List<Long> ids = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            ids.add(uploaded(memberId));
        }
        return ids;
    }

    private long uploaded(long ownerId) {
        long imageId = uploadService.issue(ownerId, command("image/jpeg", SIZE)).imageId();
        storage.markUploaded(keyOf(imageId), SIZE);
        return imageId;
    }

    private String keyOf(long imageId) {
        return jdbc.queryForObject("SELECT object_key FROM memory_image WHERE id = ?", String.class, imageId);
    }

    private String thumbnailKeyOf(long imageId) {
        return jdbc.queryForObject("SELECT thumbnail_object_key FROM memory_image WHERE id = ?", String.class, imageId);
    }

    private Long memoryIdOf(long imageId) {
        return jdbc.queryForObject("SELECT memory_id FROM memory_image WHERE id = ?", Long.class, imageId);
    }

    private long saveMember() {
        return memberRepository.saveAndFlush(aMember().build()).getId();
    }

    private static void assertInvalid(ThrowingCallable action, String field) {
        assertThatThrownBy(action)
                .isInstanceOfSatisfying(
                        InvalidFieldException.class,
                        e -> assertThat(e.getField()).isEqualTo(field));
    }
}
