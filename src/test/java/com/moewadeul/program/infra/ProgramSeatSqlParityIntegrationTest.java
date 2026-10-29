package com.moewadeul.program.infra;

import static org.assertj.core.api.Assertions.assertThat;

import com.moewadeul.common.testsupport.IntegrationTest;
import com.moewadeul.common.testsupport.MutableClock;
import com.moewadeul.member.infra.MemberJpaRepository;
import com.moewadeul.program.application.ProgramApplyFixture;
import com.moewadeul.program.domain.ProgramApplicationRepository;
import com.moewadeul.program.domain.ProgramApplicationStatus;
import com.moewadeul.program.infra.catalog.ProgramDetailRow;
import com.moewadeul.program.infra.catalog.ProgramQueryMapper;
import com.moewadeul.program.infra.enrollment.ProgramApplicationMapper;
import com.moewadeul.program.infra.enrollment.SeatClaim;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

@IntegrationTest
class ProgramSeatSqlParityIntegrationTest {

    private static final Instant NOW = MutableClock.DEFAULT_INSTANT;
    private static final List<Duration> DUE_OFFSETS =
            List.of(Duration.ofSeconds(-1), Duration.ZERO, Duration.ofSeconds(1));

    @Autowired
    private ProgramApplicationRepository applicationRepository;

    @Autowired
    private ProgramApplicationJpaRepository applicationJpaRepository;

    @Autowired
    private ProgramApplicationMapper applicationMapper;

    @Autowired
    private ProgramQueryMapper queryMapper;

    @Autowired
    private MemberJpaRepository memberRepository;

    @Autowired
    private JdbcTemplate jdbc;

    private ProgramApplyFixture fixture;

    @BeforeEach
    void setUp() {
        fixture = new ProgramApplyFixture(jdbc, memberRepository);
    }

    @Test
    @DisplayName("[PG-03] 좌석 점유 수는 도메인 저장소·MyBatis 집계·남은 자리 조회가 모두 ProgramApplication.isSeatHolderAt과 같다")
    void everySeatCountAgreesWithDomainRule() {
        // given
        SeatCase program = insertEveryStatusAndPaymentDue();

        // when
        long domainCount = applicationRepository.countSeatHolders(program.id(), NOW);
        long mapperCount = applicationMapper.countSeatHolders(program.id(), NOW);
        ProgramDetailRow detail = queryMapper.selectDetail(program.id(), NOW);

        // then
        assertThat(program.expectedHolders()).isPositive().isLessThan(program.totalRows());
        assertThat(domainCount).isEqualTo(program.expectedHolders());
        assertThat(mapperCount).isEqualTo(program.expectedHolders());
        assertThat(detail.capacity() - detail.remainingSeats()).isEqualTo(program.expectedHolders());
    }

    @Test
    @DisplayName("[PG-03] insertIfSeatAvailable은 정원이 도메인이 센 점유 수와 같으면 0, 하나 더 크면 1을 돌려준다")
    void insertIfSeatAvailableAgreesWithDomainRule() {
        // given
        SeatCase program = insertEveryStatusAndPaymentDue();
        int expected = program.expectedHolders();

        // when
        updateCapacity(program.id(), expected);
        int atCapacity = applicationMapper.insertIfSeatAvailable(claim(program.id()));
        updateCapacity(program.id(), expected + 1);
        int belowCapacity = applicationMapper.insertIfSeatAvailable(claim(program.id()));

        // then
        assertThat(atCapacity).isZero();
        assertThat(belowCapacity).isEqualTo(1);
    }

    private SeatCase insertEveryStatusAndPaymentDue() {
        int total = DUE_OFFSETS.size() * ProgramApplicationStatus.values().length;
        long programId = fixture.saveProgram(total, false);
        for (ProgramApplicationStatus status : ProgramApplicationStatus.values()) {
            for (Duration offset : DUE_OFFSETS) {
                fixture.saveApplication(programId, fixture.saveMember(), status.name(), NOW.plus(offset));
            }
        }
        int expected = (int) applicationJpaRepository.findAll().stream()
                .filter(application -> application.getProgramId() == programId)
                .filter(application -> application.isSeatHolderAt(NOW))
                .count();
        return new SeatCase(programId, total, expected);
    }

    private void updateCapacity(long programId, int capacity) {
        jdbc.update("UPDATE program SET capacity = ? WHERE id = ?", capacity, programId);
    }

    private SeatClaim claim(long programId) {
        return new SeatClaim(programId, fixture.saveMember(), NOW.plus(Duration.ofMinutes(15)), NOW);
    }

    private record SeatCase(long id, int totalRows, int expectedHolders) {}
}
