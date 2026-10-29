package com.moewadeul.program.infra.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import com.moewadeul.common.testsupport.IntegrationTest;
import com.moewadeul.common.testsupport.MutableClock;
import com.moewadeul.member.infra.MemberJpaRepository;
import com.moewadeul.program.application.ProgramApplyFixture;
import com.moewadeul.program.domain.ProgramPhase;
import com.moewadeul.program.domain.ProgramStatus;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

@IntegrationTest
class ProgramPhaseSqlParityIntegrationTest {

    private static final Instant NOW = MutableClock.DEFAULT_INSTANT;
    private static final Duration SECOND = Duration.ofSeconds(1);
    private static final Duration HOUR = Duration.ofHours(1);

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
    @DisplayName("[F-18] 목록의 단계 필터는 신청 창 경계마다 ProgramPhase.of가 그 단계라고 보는 행사만 돌려준다")
    void listFilterAgreesWithProgramPhase() {
        // given
        Map<Long, ProgramCase> cases = insertEveryBoundary();

        // when, then
        for (ProgramPhase phase : List.of(ProgramPhase.UPCOMING, ProgramPhase.OPEN, ProgramPhase.CLOSED)) {
            Set<Long> expected = cases.entrySet().stream()
                    .filter(entry -> entry.getValue().phaseAt(NOW) == phase)
                    .map(Map.Entry::getKey)
                    .collect(Collectors.toSet());
            Set<Long> listed = queryMapper.selectList(phase, NOW, 0, 1000).stream()
                    .map(ProgramListRow::programId)
                    .filter(cases::containsKey)
                    .collect(Collectors.toSet());

            assertThat(expected).as("%s", phase).isNotEmpty();
            assertThat(listed).as("%s", phase).isEqualTo(expected);
        }
    }

    private Map<Long, ProgramCase> insertEveryBoundary() {
        List<Instant[]> windows = List.of(
                new Instant[] {NOW.plus(SECOND), NOW.plus(HOUR)},
                new Instant[] {NOW, NOW.plus(HOUR)},
                new Instant[] {NOW.minus(HOUR), NOW.plus(HOUR)},
                new Instant[] {NOW.minus(HOUR), NOW},
                new Instant[] {NOW.minus(HOUR.multipliedBy(2)), NOW.minus(SECOND)});
        Map<Long, ProgramCase> cases = new HashMap<>();
        for (ProgramStatus status : ProgramStatus.values()) {
            for (Instant[] window : windows) {
                long programId = fixture.saveProgram(5, false, window[0], window[1], status.name());
                cases.put(programId, new ProgramCase(status, window[0], window[1]));
            }
        }
        return cases;
    }

    private record ProgramCase(ProgramStatus status, Instant applyOpenAt, Instant applyCloseAt) {

        ProgramPhase phaseAt(Instant now) {
            return ProgramPhase.of(status, applyOpenAt, applyCloseAt, now);
        }
    }
}
