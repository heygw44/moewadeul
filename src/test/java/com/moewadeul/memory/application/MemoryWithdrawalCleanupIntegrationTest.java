package com.moewadeul.memory.application;

import static com.moewadeul.member.domain.MemberBuilder.aMember;
import static org.assertj.core.api.Assertions.assertThat;

import com.moewadeul.common.testsupport.IntegrationTest;
import com.moewadeul.common.testsupport.TestImageStorage;
import com.moewadeul.member.infra.MemberJpaRepository;
import com.moewadeul.memory.application.image.MemoryImageUploadService;
import com.moewadeul.notification.application.outbox.OutboxPublisher;
import com.moewadeul.trust.application.withdrawal.WithdrawalService;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

@IntegrationTest
class MemoryWithdrawalCleanupIntegrationTest {

    private static final String PASSWORD = "Valid-pass1";
    private static final LocalDate YESTERDAY = LocalDate.of(2026, 10, 4);
    private static final long SIZE = 1024;

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

    @Test
    @DisplayName(
            "[F-33][PV-10] 회원이 탈퇴하고 발행기가 돌면 그 회원의 기록과 사진(방금 올린 사진 포함)이 저장소까지 모두 지워지고, 기록에 달린 좋아요도 함께 지워지며, 다른 회원의 기록은 남는다")
    void withdrawalDeletesMemoriesAndImages() {
        // given
        long member = saveMember();
        long other = saveMember();
        long attached = uploaded(member);
        long justUploaded = uploaded(member);
        List<String> keys = List.of(keyOf(attached), keyOf(justUploaded));
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
        withdrawalService.withdraw(member, PASSWORD);
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
    @DisplayName("[F-33][PV-10] 저장소 삭제가 실패해도 행은 지우고 넘어가며, 같은 회원으로 다시 정리해도 결과가 같다")
    void cleanupIsRepeatable() {
        // given
        long member = saveMember();
        long image = uploaded(member);
        commandService.write(member, write(List.of(image)));
        storage.failDeleteOf(keyOf(image));

        // when
        cleanupService.cleanUp(member);
        cleanupService.cleanUp(member);

        // then
        assertThat(count("memory WHERE member_id = ?", member)).isZero();
        assertThat(count("memory_image WHERE member_id = ?", member)).isZero();
    }

    private static MemoryWriteCommand write(List<Long> imageIds) {
        return new MemoryWriteCommand(null, 37.25, 127.25, YESTERDAY, 1, null, "PUBLIC", imageIds);
    }

    // 업로드 URL을 발급하고, 회원이 그 URL로 파일을 올린 것처럼 테스트 저장소에 객체를 둔다.
    private long uploaded(long ownerId) {
        long imageId = uploadService.issue(ownerId, "image/png", SIZE).imageId();
        storage.markUploaded(keyOf(imageId), SIZE);
        return imageId;
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
