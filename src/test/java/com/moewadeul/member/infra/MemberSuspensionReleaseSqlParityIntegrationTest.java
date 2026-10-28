package com.moewadeul.member.infra;

import static com.moewadeul.member.domain.MemberBuilder.aMember;
import static org.assertj.core.api.Assertions.assertThat;

import com.moewadeul.common.testsupport.IntegrationTest;
import com.moewadeul.common.testsupport.MutableClock;
import com.moewadeul.member.domain.Member;
import com.moewadeul.member.domain.MemberStatus;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

@IntegrationTest
class MemberSuspensionReleaseSqlParityIntegrationTest {

    private static final Instant NOW =
            MutableClock.DEFAULT_INSTANT.plusSeconds(86_400).plusNanos(123_456_000);

    private static final String COLUMNS = "status, email_verified_at, suspended_until, updated_at";

    @Autowired
    private MemberSuspensionMapper mapper;

    @Autowired
    private MemberJpaRepository memberRepository;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private JdbcTemplate jdbc;

    private enum SuspendedUntil {
        NONE(null),
        PAST(NOW.minusSeconds(1)),
        EQUAL(NOW),
        FUTURE(NOW.plusSeconds(1));

        private final Instant value;

        SuspendedUntil(Instant value) {
            this.value = value;
        }
    }

    static Stream<Arguments> everyCombination() {
        return Stream.of(MemberStatus.values())
                .flatMap(status -> Stream.of(false, true)
                        .flatMap(emailVerified -> Stream.of(SuspendedUntil.values())
                                .map(until -> Arguments.of(status, emailVerified, until))));
    }

    @ParameterizedTest(name = "{0} / 이메일 인증 {1} / 정지 종료 {2}")
    @MethodSource("everyCombination")
    @DisplayName("SQL이 정지를 푸는 회원만 도메인도 풀고, 풀린 행의 상태와 시각이 같다")
    void sqlAndDomainReleaseTheSameMembersToTheSameRow(
            MemberStatus status, boolean emailVerified, SuspendedUntil suspendedUntil) {
        // given
        long sqlSide = saveMember(status, emailVerified, suspendedUntil);

        // when
        int released = mapper.releaseExpired(NOW);
        long domainSide = saveMember(status, emailVerified, suspendedUntil);
        boolean domainReleased = releaseByDomain(domainSide);

        // then
        assertThat(domainReleased).isEqualTo(released == 1);
        assertThat(rowOf(domainSide)).isEqualTo(rowOf(sqlSide));
    }

    private long saveMember(MemberStatus status, boolean emailVerified, SuspendedUntil suspendedUntil) {
        Member member = memberRepository.saveAndFlush(aMember().build());
        jdbc.update(
                "UPDATE member SET status = ?, email_verified_at = ?, suspended_until = ? WHERE id = ?",
                status.name(),
                emailVerified ? utc(MutableClock.DEFAULT_INSTANT) : null,
                suspendedUntil.value == null ? null : utc(suspendedUntil.value),
                member.getId());
        return member.getId();
    }

    private boolean releaseByDomain(long id) {
        return transactionTemplate.execute(tx -> {
            Member member = memberRepository.findById(id).orElseThrow();
            boolean released = member.releaseSuspensionIfExpired(NOW);
            memberRepository.saveAndFlush(member);
            return released;
        });
    }

    private Map<String, Object> rowOf(long id) {
        return jdbc.queryForMap("SELECT " + COLUMNS + " FROM member WHERE id = ?", id);
    }

    private static LocalDateTime utc(Instant instant) {
        return LocalDateTime.ofInstant(instant, ZoneOffset.UTC);
    }
}
