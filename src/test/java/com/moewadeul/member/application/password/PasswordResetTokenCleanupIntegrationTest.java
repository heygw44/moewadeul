package com.moewadeul.member.application.password;

import static com.moewadeul.member.domain.MemberBuilder.aMember;
import static org.assertj.core.api.Assertions.assertThat;

import com.moewadeul.common.testsupport.IntegrationTest;
import com.moewadeul.common.testsupport.MutableClock;
import com.moewadeul.member.domain.password.PasswordResetPolicy;
import com.moewadeul.member.infra.MemberJpaRepository;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

@IntegrationTest
class PasswordResetTokenCleanupIntegrationTest {

    private static final Duration ONE_SECOND = Duration.ofSeconds(1);
    private static final DateTimeFormatter DATETIME_UTC =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSSSSS").withZone(ZoneOffset.UTC);

    private final AtomicInteger hashSequence = new AtomicInteger();

    @Autowired
    private PasswordResetTokenCleanupService cleanupService;

    @Autowired
    private MemberJpaRepository memberRepository;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private MutableClock clock;

    private long memberId;

    @BeforeEach
    void setUp() {
        memberId = memberRepository.saveAndFlush(aMember().build()).getId();
    }

    @Test
    @DisplayName("[F-02][PW-02] 만료된 지 하루가 넘은 토큰은 쓴 것이든 안 쓴 것이든 지운다")
    void deletesTokensExpiredMoreThanOneDayAgo() {
        long unused = insertToken(cutoff().minus(ONE_SECOND), false);
        long used = insertToken(cutoff().minus(Duration.ofDays(30)), true);

        int deleted = cleanupService.deleteExpiredTokens();

        assertThat(deleted).isEqualTo(2);
        assertThat(exists(unused)).isFalse();
        assertThat(exists(used)).isFalse();
    }

    @Test
    @DisplayName("[F-02][PW-02] 정확히 하루 전에 만료된 토큰은 지운다")
    void deletesTokenExpiredExactlyOneDayAgo() {
        long exactly = insertToken(cutoff(), false);

        int deleted = cleanupService.deleteExpiredTokens();

        assertThat(deleted).isEqualTo(1);
        assertThat(exists(exactly)).isFalse();
    }

    @Test
    @DisplayName("[F-02][PW-02] 만료된 지 하루가 안 된 토큰과 아직 유효한 토큰은 남긴다")
    void keepsRecentlyExpiredAndValidTokens() {
        long justUnder = insertToken(cutoff().plus(ONE_SECOND), false);
        long usedJustUnder = insertToken(cutoff().plus(ONE_SECOND), true);
        long valid = insertToken(clock.instant().plus(PasswordResetPolicy.TOKEN_VALIDITY), false);

        int deleted = cleanupService.deleteExpiredTokens();

        assertThat(deleted).isZero();
        assertThat(exists(justUnder)).isTrue();
        assertThat(exists(usedJustUnder)).isTrue();
        assertThat(exists(valid)).isTrue();
    }

    @Test
    @DisplayName("[F-02][PW-02] 한 번에 지우는 상한보다 대상이 많아도 모두 지우고 남길 토큰은 남긴다")
    void deletesAllExpiredTokensBeyondBatchSize() {
        int total = PasswordResetTokenCleanupService.BATCH_SIZE * 2 + 1;
        insertExpiredTokens(total);
        long valid = insertToken(clock.instant().plus(PasswordResetPolicy.TOKEN_VALIDITY), false);

        int deleted = cleanupService.deleteExpiredTokens();

        assertThat(deleted).isEqualTo(total);
        assertThat(count()).isEqualTo(1);
        assertThat(exists(valid)).isTrue();
    }

    @Test
    @DisplayName("[F-02][PW-02] 지울 것이 없으면 0을 돌려준다")
    void returnsZeroWhenNothingToDelete() {
        assertThat(cleanupService.deleteExpiredTokens()).isZero();
    }

    private Instant cutoff() {
        return clock.instant().minus(PasswordResetPolicy.TOKEN_RETENTION_AFTER_EXPIRY);
    }

    private long insertToken(Instant expiresAt, boolean used) {
        String at = DATETIME_UTC.format(expiresAt);
        jdbc.update(
                "INSERT INTO password_reset_token (member_id, token_hash, expires_at, used_at, created_at, updated_at)"
                        + " VALUES (?, ?, ?, ?, ?, ?)",
                memberId,
                nextHash(),
                at,
                used ? at : null,
                at,
                at);
        return jdbc.queryForObject("SELECT MAX(id) FROM password_reset_token", Long.class);
    }

    private void insertExpiredTokens(int count) {
        String at = DATETIME_UTC.format(cutoff().minus(ONE_SECOND));
        List<Object[]> rows = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            rows.add(new Object[] {memberId, nextHash(), at, at, at});
        }
        jdbc.batchUpdate(
                "INSERT INTO password_reset_token (member_id, token_hash, expires_at, created_at, updated_at)"
                        + " VALUES (?, ?, ?, ?, ?)",
                rows);
    }

    private String nextHash() {
        return "%064x".formatted(hashSequence.incrementAndGet());
    }

    private boolean exists(long id) {
        Integer found =
                jdbc.queryForObject("SELECT COUNT(*) FROM password_reset_token WHERE id = ?", Integer.class, id);
        return found != null && found == 1;
    }

    private int count() {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM password_reset_token", Integer.class);
        return count == null ? 0 : count;
    }
}
