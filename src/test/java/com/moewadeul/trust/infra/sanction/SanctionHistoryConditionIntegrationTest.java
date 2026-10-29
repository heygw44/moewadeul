package com.moewadeul.trust.infra.sanction;

import static org.assertj.core.api.Assertions.assertThat;

import com.moewadeul.common.testsupport.IntegrationTest;
import com.moewadeul.common.testsupport.MutableClock;
import com.moewadeul.common.testsupport.TestSequence;
import com.moewadeul.member.infra.MemberJpaRepository;
import com.moewadeul.trust.application.CompanionReviewFixture;
import com.moewadeul.trust.domain.sanction.Sanction;
import com.moewadeul.trust.domain.sanction.SanctionPolicy;
import com.moewadeul.trust.domain.sanction.SanctionStatus;
import com.moewadeul.trust.domain.sanction.SanctionType;
import com.moewadeul.trust.infra.profile.TrustRecordMapper;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

@IntegrationTest
class SanctionHistoryConditionIntegrationTest {

    private static final Instant NOW = MutableClock.DEFAULT_INSTANT;
    private static final Duration RECENT_PERIOD = Duration.ofDays(180);

    @Autowired
    private SanctionJpaRepository sanctionRepository;

    @Autowired
    private TrustRecordMapper trustRecordMapper;

    @Autowired
    private MemberJpaRepository memberRepository;

    @Autowired
    private JdbcTemplate jdbc;

    private CompanionReviewFixture fixture;

    @BeforeEach
    void setUp() {
        fixture = new CompanionReviewFixture(jdbc, memberRepository);
    }

    @Test
    @DisplayName(
            "[SN-10][TR-01][PV-05] 제재 종류와 상태의 모든 조합에서 다음 단계와 신뢰 회원 판정 쿼리는 countsAsHistory(), 탈퇴 CI 보관 쿼리는 countsForCiRetention()과 같다")
    void queriesFollowSanctionConditions() {
        for (SanctionType type : SanctionType.values()) {
            for (SanctionStatus status : SanctionStatus.values()) {
                long memberId = fixture.saveVerifiedMember(TestSequence.nickname());
                insertSanction(memberId, type, status);
                Sanction sanction = sanctionOf(type, status);
                boolean expected = sanction.countsAsHistory();

                assertThat(sanctionRepository
                                .findHighestConfirmedLevel(memberId)
                                .isPresent())
                        .as("findHighestConfirmedLevel %s %s", type, status)
                        .isEqualTo(expected);
                assertThat(sanctionRepository.existsCiRetentionSanction(memberId))
                        .as("existsCiRetentionSanction %s %s", type, status)
                        .isEqualTo(sanction.countsForCiRetention());
                assertThat(trustRecordMapper
                                .selectCompanionRecord(memberId, NOW, NOW.minus(RECENT_PERIOD))
                                .recentSanction())
                        .as("recent_sanction %s %s", type, status)
                        .isEqualTo(expected);
            }
        }
    }

    private static Sanction sanctionOf(SanctionType type, SanctionStatus status) {
        Sanction sanction = Sanction.temporary(1L, 1L, NOW);
        ReflectionTestUtils.setField(sanction, "type", type);
        ReflectionTestUtils.setField(sanction, "status", status);
        return sanction;
    }

    private void insertSanction(long memberId, SanctionType type, SanctionStatus status) {
        Integer level = type == SanctionType.TEMPORARY_72H ? null : SanctionPolicy.levelOf(type);
        jdbc.update(
                "INSERT INTO sanction (member_id, type, level, reason, starts_at, status, created_at, updated_at)"
                        + " VALUES (?, ?, ?, '테스트', ?, ?, NOW(6), NOW(6))",
                memberId,
                type.name(),
                level,
                LocalDateTime.ofInstant(NOW.minus(Duration.ofDays(1)), ZoneOffset.UTC),
                status.name());
    }
}
