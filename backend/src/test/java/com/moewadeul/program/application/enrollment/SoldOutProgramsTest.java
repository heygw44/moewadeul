package com.moewadeul.program.application.enrollment;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SoldOutProgramsTest {

    private static final Instant NOW = Instant.parse("2026-11-11T03:00:00Z");
    private static final Instant CLOSES_LATER = NOW.plus(Duration.ofDays(1));
    private static final long PROGRAM = 7L;
    private static final long OTHER_PROGRAM = 8L;
    private static final long HOLDER = 1L;
    private static final long OUTSIDER = 2L;
    private static final long LATE = 3L;

    private final SoldOutPrograms soldOut = new SoldOutPrograms();

    @Test
    @DisplayName("[F-18] 매진 표시는 TTL 동안만 자리 없는 회원을 거절하고, 자리를 쥔 회원은 거절하지 않는다")
    void markRejectsOutsidersUntilTtl() {
        // given
        soldOut.mark(soldOut.read(PROGRAM, () -> List.of(HOLDER)), CLOSES_LATER, NOW);

        // when, then
        assertThat(soldOut.rejects(PROGRAM, OUTSIDER, NOW)).isTrue();
        assertThat(soldOut.rejects(
                        PROGRAM, OUTSIDER, NOW.plus(SoldOutPrograms.TTL).minusNanos(1)))
                .isTrue();
        assertThat(soldOut.rejects(PROGRAM, HOLDER, NOW)).isFalse();
        assertThat(soldOut.rejects(OTHER_PROGRAM, OUTSIDER, NOW)).isFalse();
        assertThat(soldOut.rejects(PROGRAM, OUTSIDER, NOW.plus(SoldOutPrograms.TTL)))
                .isFalse();
        assertThat(soldOut.rejects(PROGRAM, OUTSIDER, NOW)).isFalse();
    }

    @Test
    @DisplayName("[F-18] 신청 마감이 TTL보다 먼저 오면 마감 시각에 표시가 끝나 마감 뒤 신청은 원래 검사를 받는다")
    void markEndsAtApplyClose() {
        // given
        Instant closesAt = NOW.plusMillis(300);
        soldOut.mark(soldOut.read(PROGRAM, () -> List.of(HOLDER)), closesAt, NOW);

        // when, then
        assertThat(soldOut.rejects(PROGRAM, OUTSIDER, closesAt.minusNanos(1))).isTrue();
        assertThat(soldOut.rejects(PROGRAM, OUTSIDER, closesAt)).isFalse();
    }

    @Test
    @DisplayName("[F-18][NFR-03] 자리 차지 회원을 읽는 사이 같은 행사에 커밋된 신청이 있으면 표시하지 않는다. 읽은 목록에 그 회원이 빠졌을 수 있다")
    void admissionDuringReadSkipsMark() {
        // given
        SoldOutPrograms.SeatHolders holders = soldOut.read(PROGRAM, () -> {
            soldOut.beforeCommit(PROGRAM, LATE);
            soldOut.afterCompletion(PROGRAM, LATE, true);
            return List.of(HOLDER);
        });

        // when
        soldOut.mark(holders, CLOSES_LATER, NOW);

        // then
        assertThat(soldOut.rejects(PROGRAM, LATE, NOW)).isFalse();
        assertThat(soldOut.rejects(PROGRAM, OUTSIDER, NOW)).isFalse();
    }

    @Test
    @DisplayName("[F-18] 읽는 사이 다른 행사에 커밋된 신청은 이 행사의 표시를 막지 않는다")
    void admissionToOtherProgramKeepsMark() {
        // given
        SoldOutPrograms.SeatHolders holders = soldOut.read(PROGRAM, () -> {
            soldOut.beforeCommit(OTHER_PROGRAM, LATE);
            soldOut.afterCompletion(OTHER_PROGRAM, LATE, true);
            return List.of(HOLDER);
        });

        // when
        soldOut.mark(holders, CLOSES_LATER, NOW);

        // then
        assertThat(soldOut.rejects(PROGRAM, OUTSIDER, NOW)).isTrue();
    }

    @Test
    @DisplayName("[F-18][NFR-03] 표시가 있는 동안 자리를 얻는 회원은 커밋 직전부터 거절되지 않고, 롤백되면 다시 거절된다")
    void committingMemberIsNeverRejected() {
        // given
        soldOut.mark(soldOut.read(PROGRAM, () -> List.of(HOLDER)), CLOSES_LATER, NOW);

        // when
        soldOut.beforeCommit(PROGRAM, LATE);
        boolean rejectedWhileCommitting = soldOut.rejects(PROGRAM, LATE, NOW);
        soldOut.afterCompletion(PROGRAM, LATE, true);
        soldOut.beforeCommit(PROGRAM, OUTSIDER);
        soldOut.afterCompletion(PROGRAM, OUTSIDER, false);

        // then
        assertThat(rejectedWhileCommitting).isFalse();
        assertThat(soldOut.rejects(PROGRAM, LATE, NOW)).isFalse();
        assertThat(soldOut.rejects(PROGRAM, OUTSIDER, NOW)).isTrue();
    }
}
