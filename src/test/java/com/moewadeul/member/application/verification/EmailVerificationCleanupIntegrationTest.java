package com.moewadeul.member.application.verification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.moewadeul.common.error.RateLimitedException;
import com.moewadeul.common.testsupport.IntegrationTest;
import com.moewadeul.common.testsupport.MutableClock;
import com.moewadeul.member.domain.Member;
import com.moewadeul.member.domain.MemberBuilder;
import com.moewadeul.member.domain.MemberErrorCode;
import com.moewadeul.member.domain.verification.EmailVerificationPolicy;
import com.moewadeul.member.infra.MemberJpaRepository;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

@IntegrationTest
class EmailVerificationCleanupIntegrationTest {

    private static final String IP = "203.0.113.7";
    private static final Duration ONE_SECOND = Duration.ofSeconds(1);
    // DATETIME 컬럼에 UTC 시각을 문자열로 넣는다. 드라이버의 시간대 변환이 끼어들지 않게 하려는 것이다.
    private static final DateTimeFormatter DATETIME_UTC =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSSSSS").withZone(ZoneOffset.UTC);

    @Autowired
    private EmailVerificationCleanupService cleanupService;

    @Autowired
    private EmailVerificationService emailVerificationService;

    @Autowired
    private MemberJpaRepository memberRepository;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private MutableClock clock;

    @Test
    @DisplayName("[F-01][PV-12] 7일보다 1초 더 오래된 인증 기록은 지우고, 정확히 7일 된 기록과 그보다 새 기록은 남긴다")
    void deletesOnlyRecordsOlderThanSevenDays() {
        Member member = newMember();
        Instant cutoff = cutoff();
        long older = insertRecord(member, IP, cutoff.minus(ONE_SECOND));
        long exact = insertRecord(member, IP, cutoff);
        long newer = insertRecord(member, IP, cutoff.plus(ONE_SECOND));

        int deleted = cleanupService.deleteExpired();

        assertThat(deleted).isEqualTo(1);
        assertThat(exists(older)).isFalse();
        assertThat(exists(exact)).isTrue();
        assertThat(exists(newer)).isTrue();
    }

    @Test
    @DisplayName("[F-01][PV-12] 한 번에 지우는 상한보다 대상이 많아도 모두 지우고, 새 기록은 남긴다")
    void deletesAllExpiredRowsBeyondBatchSize() {
        Member member = newMember();
        int total = EmailVerificationCleanupService.BATCH_SIZE + 1;
        Instant expiredAt = cutoff().minus(ONE_SECOND);
        for (int i = 0; i < total; i++) {
            insertRecord(member, IP, expiredAt);
        }
        long recent = insertRecord(member, IP, clock.instant());

        int deleted = cleanupService.deleteExpired();

        assertThat(deleted).isEqualTo(total);
        assertThat(count()).isEqualTo(1);
        assertThat(exists(recent)).isTrue();
    }

    @Test
    @DisplayName("[F-01][EV-03][PV-12] 오래된 기록을 지워도 최근 24시간의 발송 5번은 남아 하루 한도에 걸린다")
    void dailyLimitStillAppliesAfterCleanup() {
        Member member = newMember();
        insertRecord(member, IP, cutoff().minus(ONE_SECOND));
        for (int hoursAgo = 2; hoursAgo <= 6; hoursAgo++) {
            insertRecord(member, IP, clock.instant().minus(Duration.ofHours(hoursAgo)));
        }

        int deleted = cleanupService.deleteExpired();

        assertThat(deleted).isEqualTo(1);
        assertThatThrownBy(() -> emailVerificationService.requestResend(member.getId(), IP))
                .isInstanceOfSatisfying(
                        RateLimitedException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(MemberErrorCode.EMAIL_RESEND_LIMITED));
    }

    @Test
    @DisplayName("[F-01][EV-03][PV-12] 오래된 기록을 지워도 같은 IP가 최근 1시간에 보낸 20번은 남아 다른 회원의 재발송도 막는다")
    void ipLimitStillAppliesAfterCleanup() {
        for (int i = 0; i < EmailVerificationPolicy.IP_LIMIT; i++) {
            insertRecord(newMember(), IP, clock.instant().minus(Duration.ofMinutes(10)));
        }
        insertRecord(newMember(), IP, cutoff().minus(ONE_SECOND));
        Member another = newMember();

        int deleted = cleanupService.deleteExpired();

        assertThat(deleted).isEqualTo(1);
        assertThatThrownBy(() -> emailVerificationService.requestResend(another.getId(), IP))
                .isInstanceOfSatisfying(
                        RateLimitedException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(MemberErrorCode.EMAIL_RESEND_LIMITED));
    }

    @Test
    @DisplayName("[F-01][PV-12] 지울 것이 없으면 0을 돌려준다")
    void returnsZeroWhenNothingToDelete() {
        insertRecord(newMember(), IP, clock.instant());

        assertThat(cleanupService.deleteExpired()).isZero();
        assertThat(count()).isEqualTo(1);
    }

    @Test
    @DisplayName("[F-01][EV-03] 마지막 요청이 24시간보다 1마이크로초 더 지난 IP 잠금 행은 지우고, 정확히 24시간 된 행과 그보다 새 행은 남긴다")
    void deletesOnlyIpLockRowsIdleLongerThanOneDay() {
        Instant lockCutoff = clock.instant().minus(Duration.ofHours(24));
        insertIpLockRow("idle", lockCutoff.minus(Duration.ofNanos(1_000)));
        insertIpLockRow("exact", lockCutoff);
        insertIpLockRow("recent", clock.instant());

        cleanupService.deleteExpired();

        assertThat(jdbc.queryForList("SELECT ip_key FROM email_verification_ip_lock ORDER BY ip_key", String.class))
                .containsExactly(ipLockKey("exact"), ipLockKey("recent"));
    }

    private Instant cutoff() {
        return clock.instant().minus(EmailVerificationPolicy.RECORD_RETENTION);
    }

    private Member newMember() {
        return memberRepository.saveAndFlush(
                MemberBuilder.aMember().now(clock.instant()).build());
    }

    private long insertRecord(Member member, String requestIp, Instant createdAt) {
        String at = DATETIME_UTC.format(createdAt);
        jdbc.update(
                "INSERT INTO email_verification"
                        + " (member_id, code_hash, request_ip, attempt_count, expires_at, created_at, updated_at)"
                        + " VALUES (?, ?, ?, 0, ?, ?, ?)",
                member.getId(),
                "a".repeat(64),
                requestIp,
                DATETIME_UTC.format(createdAt.plus(EmailVerificationPolicy.CODE_VALIDITY)),
                at,
                at);
        return jdbc.queryForObject("SELECT MAX(id) FROM email_verification", Long.class);
    }

    private void insertIpLockRow(String name, Instant lastRequestedAt) {
        jdbc.update(
                "INSERT INTO email_verification_ip_lock (ip_key, last_requested_at) VALUES (?, ?)",
                ipLockKey(name),
                DATETIME_UTC.format(lastRequestedAt));
    }

    // 잠금 행의 키는 64자 고정 길이다. 테스트는 이름 뒤를 채워서 행마다 다르고 정렬 순서를 아는 키를 만든다.
    private static String ipLockKey(String name) {
        return (name + "_".repeat(64)).substring(0, 64);
    }

    private boolean exists(long id) {
        Integer found = jdbc.queryForObject("SELECT COUNT(*) FROM email_verification WHERE id = ?", Integer.class, id);
        return found != null && found == 1;
    }

    private int count() {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM email_verification", Integer.class);
        return count == null ? 0 : count;
    }
}
