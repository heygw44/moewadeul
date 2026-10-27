package com.moewadeul.memory.application;

import static com.moewadeul.member.domain.MemberBuilder.aMember;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.moewadeul.common.testsupport.IntegrationTest;
import com.moewadeul.common.testsupport.MutableClock;
import com.moewadeul.common.testsupport.TestImageStorage;
import com.moewadeul.member.infra.MemberJpaRepository;
import com.moewadeul.memory.application.image.MemoryImageUploadCommand;
import com.moewadeul.memory.application.image.MemoryImageUploadService;
import com.moewadeul.outbox.application.OutboxPublisher;
import com.moewadeul.trust.application.withdrawal.WithdrawalService;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

@IntegrationTest
class MemoryWithdrawalCleanupIntegrationTest {

    private static final String PASSWORD = "Valid-pass1";
    private static final String REQUEST_IP = "203.0.113.7";
    private static final LocalDate YESTERDAY = LocalDate.of(2026, 10, 4);
    private static final long SIZE = 1024;
    private static final long THUMBNAIL_SIZE = 64;

    @Autowired
    private WithdrawalService withdrawalService;

    @Autowired
    private OutboxPublisher outboxPublisher;

    @Autowired
    private MemoryCommandService commandService;

    @Autowired
    private MemoryImageUploadService uploadService;

    @Autowired
    private MemoryWithdrawalCleanupService cleanupService;

    @Autowired
    private MemberJpaRepository memberRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private TestImageStorage storage;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private MutableClock clock;

    @Test
    @DisplayName(
            "[F-33][PV-10] 회원이 탈퇴하고 발행기가 돌면 그 회원의 기록과 사진(방금 올린 사진 포함)이 썸네일과 함께 저장소까지 모두 지워지고, 기록에 달린 좋아요도 함께 지워지며, 다른 회원의 기록은 남는다")
    void withdrawalDeletesMemoriesAndImages() {
        // given
        long member = saveMember();
        long other = saveMember();
        long attached = uploaded(member);
        long justUploaded = uploaded(member);
        List<String> keys =
                List.of(keyOf(attached), thumbnailKeyOf(attached), keyOf(justUploaded), thumbnailKeyOf(justUploaded));
        commandService.write(member, write(List.of(attached)));
        commandService.write(member, write(List.of()));
        long othersImage = uploaded(other);
        long othersMemory = commandService.write(other, write(List.of(othersImage)));
        long ownMemory = commandService.write(member, write(List.of()));
        jdbc.update(
                "INSERT INTO memory_like (memory_id, member_id, created_at) VALUES (?, ?, NOW(6))", ownMemory, other);
        jdbc.update(
                "INSERT INTO memory_like (memory_id, member_id, created_at) VALUES (?, ?, NOW(6))",
                othersMemory,
                other);

        // when
        withdrawalService.withdraw(member, PASSWORD, REQUEST_IP);
        outboxPublisher.publishPending();

        // then
        assertThat(count("memory WHERE member_id = ?", member)).isZero();
        assertThat(count("memory_image WHERE member_id = ?", member)).isZero();
        assertThat(storage.deletedKeys()).containsExactlyInAnyOrderElementsOf(keys);
        assertThat(count("memory_like WHERE memory_id = ?", ownMemory)).isZero();
        assertThat(count("memory_like WHERE memory_id = ?", othersMemory)).isEqualTo(1);
        assertThat(count("memory WHERE id = ?", othersMemory)).isEqualTo(1);
        assertThat(storage.hasObject(keyOf(othersImage))).isTrue();
        assertThat(jdbc.queryForObject(
                        "SELECT status FROM outbox_event WHERE event_type = 'WITHDRAWAL_MEMORY_CLEANUP' AND aggregate_id = ?",
                        String.class,
                        member))
                .isEqualTo("PUBLISHED");
    }

    @Test
    @DisplayName("[F-33][PV-10] 사진 객체를 지우지 못하면 그 사진 행을 남기고 예외를 던지며, 다른 사진은 계속 지우고, 저장소가 돌아온 뒤 다시 정리하면 객체와 행이 모두 지워진다")
    void failedObjectDeleteKeepsRowUntilRetrySucceeds() {
        // given
        long member = saveMember();
        long failing = uploaded(member);
        long other = uploaded(member);
        commandService.write(member, write(List.of(failing)));
        String failingKey = keyOf(failing);
        String failingThumbnailKey = thumbnailKeyOf(failing);
        String otherKey = keyOf(other);
        storage.failDeleteOf(failingKey);

        // when
        assertThatThrownBy(() -> cleanupService.cleanUp(member)).isInstanceOf(IllegalStateException.class);

        // then
        assertThat(count("memory WHERE member_id = ?", member)).isZero();
        assertThat(count("memory_image WHERE id = ?", failing)).isEqualTo(1);
        assertThat(count("memory_image WHERE id = ?", other)).isZero();
        assertThat(storage.hasObject(failingKey)).isTrue();
        assertThat(storage.hasObject(otherKey)).isFalse();

        // when
        storage.allowDeleteOf(failingKey);
        cleanupService.cleanUp(member);

        // then
        assertThat(count("memory_image WHERE member_id = ?", member)).isZero();
        assertThat(storage.hasObject(failingKey)).isFalse();
        assertThat(storage.hasObject(failingThumbnailKey)).isFalse();
    }

    @Test
    @DisplayName("[F-33][PV-10] 원본은 지웠지만 썸네일 객체를 지우지 못하면 그 사진 행을 남기고, 다시 정리하면 썸네일과 행이 지워진다")
    void failedThumbnailDeleteKeepsRow() {
        // given
        long member = saveMember();
        long image = uploaded(member);
        String key = keyOf(image);
        String thumbnailKey = thumbnailKeyOf(image);
        storage.failDeleteOf(thumbnailKey);

        // when
        assertThatThrownBy(() -> cleanupService.cleanUp(member)).isInstanceOf(IllegalStateException.class);

        // then
        assertThat(count("memory_image WHERE id = ?", image)).isEqualTo(1);
        assertThat(storage.hasObject(key)).isFalse();
        assertThat(storage.hasObject(thumbnailKey)).isTrue();

        // when
        storage.allowDeleteOf(thumbnailKey);
        cleanupService.cleanUp(member);

        // then
        assertThat(count("memory_image WHERE id = ?", image)).isZero();
        assertThat(storage.hasObject(thumbnailKey)).isFalse();
    }

    @Test
    @DisplayName("[F-33][PV-10] 탈퇴 정리 중 저장소 삭제가 실패하면 이벤트는 발행 완료가 되지 않고 남아, 저장소가 돌아온 뒤 재시도에서 사진이 모두 지워진다")
    void outboxEventRetriesUntilObjectsAreDeleted() {
        // given
        long member = saveMember();
        long image = uploaded(member);
        String key = keyOf(image);
        storage.failDeleteOf(key);
        withdrawalService.withdraw(member, PASSWORD, REQUEST_IP);

        // when
        outboxPublisher.publishPending();

        // then
        Map<String, Object> event = cleanupEvent(member);
        assertThat(event.get("status")).isEqualTo("PENDING");
        assertThat(((Number) event.get("attempt_count")).intValue()).isEqualTo(1);
        assertThat(count("memory_image WHERE id = ?", image)).isEqualTo(1);
        assertThat(storage.hasObject(key)).isTrue();

        // when
        storage.allowDeleteOf(key);
        clock.advance(Duration.ofHours(1));
        outboxPublisher.publishPending();

        // then
        assertThat(cleanupEvent(member).get("status")).isEqualTo("PUBLISHED");
        assertThat(count("memory_image WHERE id = ?", image)).isZero();
        assertThat(storage.hasObject(key)).isFalse();
    }

    private Map<String, Object> cleanupEvent(long memberId) {
        return jdbc.queryForMap(
                "SELECT status, attempt_count FROM outbox_event"
                        + " WHERE event_type = 'WITHDRAWAL_MEMORY_CLEANUP' AND aggregate_id = ?",
                memberId);
    }

    private static MemoryWriteCommand write(List<Long> imageIds) {
        return new MemoryWriteCommand(null, 37.25, 127.25, YESTERDAY, 1, null, "PUBLIC", imageIds);
    }

    private long uploaded(long ownerId) {
        long imageId = uploadService
                .issue(ownerId, new MemoryImageUploadCommand("image/png", SIZE, THUMBNAIL_SIZE))
                .imageId();
        storage.markUploaded(keyOf(imageId), SIZE);
        storage.markUploaded(thumbnailKeyOf(imageId), THUMBNAIL_SIZE);
        return imageId;
    }

    private String thumbnailKeyOf(long imageId) {
        return jdbc.queryForObject("SELECT thumbnail_object_key FROM memory_image WHERE id = ?", String.class, imageId);
    }

    private String keyOf(long imageId) {
        return jdbc.queryForObject("SELECT object_key FROM memory_image WHERE id = ?", String.class, imageId);
    }

    private int count(String fromWhere, long id) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM " + fromWhere, Integer.class, id);
    }

    private long saveMember() {
        return memberRepository
                .saveAndFlush(
                        aMember().passwordHash(passwordEncoder.encode(PASSWORD)).build())
                .getId();
    }
}
