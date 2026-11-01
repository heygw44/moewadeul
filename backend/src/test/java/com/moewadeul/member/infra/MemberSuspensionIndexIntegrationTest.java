package com.moewadeul.member.infra;

import static org.assertj.core.api.Assertions.assertThat;

import com.moewadeul.common.testsupport.IntegrationTest;
import com.moewadeul.common.testsupport.MutableClock;
import com.moewadeul.program.application.ProgramApplyFixture;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

@IntegrationTest
class MemberSuspensionIndexIntegrationTest {

    private static final Instant NOW = MutableClock.DEFAULT_INSTANT.plusSeconds(86_400);

    private static final String RELEASE_SQL = "UPDATE member SET status = 'ACTIVE', suspended_until = NULL"
            + " WHERE status = 'SUSPENDED' AND suspended_until IS NOT NULL AND suspended_until <= ?";

    private static final int ACTIVE_MEMBERS = 300;

    @Autowired
    private MemberSuspensionMapper mapper;

    @Autowired
    private MemberJpaRepository memberRepository;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    @DisplayName("[F-21][SN-10] 정지 해제는 (status, suspended_until) 인덱스로 기한이 지난 정지 회원만 읽고 푼다")
    void expiredSuspensionsAreFoundThroughStatusSuspendedUntilIndex() {
        // given
        ProgramApplyFixture fixture = new ProgramApplyFixture(jdbc, memberRepository);
        fixture.saveEmailVerifiedMembers(ACTIVE_MEMBERS);
        long suspended = fixture.saveMember();
        jdbc.update(
                "UPDATE member SET status = 'SUSPENDED', suspended_until = ? WHERE id = ?",
                utc(NOW.minusSeconds(1)),
                suspended);
        jdbc.execute("ANALYZE TABLE member");

        // when
        Map<String, Object> plan = jdbc.queryForMap("EXPLAIN " + RELEASE_SQL, utc(NOW));
        mapper.releaseExpired(NOW);

        // then
        assertThat(plan.get("key")).isEqualTo("idx_member_status_suspended_until");
        assertThat(jdbc.queryForObject("SELECT status FROM member WHERE id = ?", String.class, suspended))
                .isNotEqualTo("SUSPENDED");
    }

    private static LocalDateTime utc(Instant instant) {
        return LocalDateTime.ofInstant(instant, ZoneOffset.UTC);
    }
}
