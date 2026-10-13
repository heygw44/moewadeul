package com.moewadeul.memory.application;

import static com.moewadeul.member.domain.MemberBuilder.aMember;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.moewadeul.common.error.InvalidFieldException;
import com.moewadeul.common.testsupport.IntegrationTest;
import com.moewadeul.common.testsupport.MutableClock;
import com.moewadeul.common.testsupport.TestImageStorage;
import com.moewadeul.common.web.PatchField;
import com.moewadeul.member.infra.MemberJpaRepository;
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
        MemoryImageUpload upload = uploadService.issue(memberId, "image/webp", SIZE);

        // then
        assertThat(keyOf(upload.imageId()))
                .startsWith("memory/" + memberId + "/")
                .endsWith(".webp");
        assertThat(memoryIdOf(upload.imageId())).isNull();
        assertThat(upload.headers()).containsEntry("Content-Type", "image/webp");
    }

    @Test
    @DisplayName("[F-33][MM-05] 허용하지 않는 형식이나 5MB를 넘는 크기는 INVALID_INPUT으로 거부한다")
    void issueRejectsTypeAndSize() {
        assertInvalid(() -> uploadService.issue(memberId, "image/gif", SIZE), "contentType");
        assertInvalid(() -> uploadService.issue(memberId, "image/png", 5L * 1024 * 1024 + 1), "sizeBytes");
    }

    @Test
    @DisplayName("[F-33][MM-05] 사진 10장은 붙이고, 11장, 같은 ID 두 번, 저장소에 없는 사진, 다른 기록에 붙은 사진은 imageIds 오류로 거부한다")
    void attachRules() {
        // given
        List<Long> ten = uploadedImages(10);
        List<Long> eleven = uploadedImages(11);
        long mine = uploaded(memberId);
        long notUploaded = uploadService.issue(memberId, "image/png", SIZE).imageId();
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

    // 업로드 URL을 발급하고, 회원이 그 URL로 파일을 올린 것처럼 테스트 저장소에 객체를 둔다.
    private long uploaded(long ownerId) {
        long imageId = uploadService.issue(ownerId, "image/jpeg", SIZE).imageId();
        storage.markUploaded(keyOf(imageId), SIZE);
        return imageId;
    }

    private String keyOf(long imageId) {
        return jdbc.queryForObject("SELECT object_key FROM memory_image WHERE id = ?", String.class, imageId);
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
