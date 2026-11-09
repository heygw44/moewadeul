package com.moewadeul.member.domain.login;

import static org.assertj.core.api.Assertions.assertThat;

import com.moewadeul.common.testsupport.MutableClock;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class LoginLockoutTest {

    private static final String IP = "198.51.100.1";
    private static final String OTHER_IP = "198.51.100.2";

    private final MutableClock clock = MutableClock.atDefaultInstant();

    @Test
    @DisplayName("[PW-03] 실패가 4건이면 잠그지 않는다")
    void fourFailuresDoNotLock() {
        List<LoginFailure> failures = failuresMinutesAgo(4, 3, 2, 1);

        Optional<Instant> lockedUntil = LoginLockout.lockedUntil(failures, IP, now(clock));

        assertThat(lockedUntil).isEmpty();
    }

    @Test
    @DisplayName("[PW-03] 10분 안에 실패가 5건이면 가장 오래된 실패로부터 10분 뒤까지 잠근다")
    void fiveFailuresLockUntilTenMinutesAfterOldestFailure() {
        List<LoginFailure> failures = failuresMinutesAgo(9, 7, 5, 3, 1);

        Optional<Instant> lockedUntil = LoginLockout.lockedUntil(failures, IP, now(clock));

        assertThat(lockedUntil).contains(now(clock).minus(Duration.ofMinutes(9)).plus(LoginLockout.WINDOW));
    }

    @Test
    @DisplayName("[PW-03] 가장 오래된 실패가 정확히 10분 전이면 구간 밖이라 실패를 4건으로 세고 잠그지 않는다")
    void failureExactlyTenMinutesAgoIsOutsideWindow() {
        List<LoginFailure> failures = failuresMinutesAgo(10, 6, 4, 2, 1);

        Optional<Instant> lockedUntil = LoginLockout.lockedUntil(failures, IP, now(clock));

        assertThat(lockedUntil).isEmpty();
    }

    @Test
    @DisplayName("[PW-03] 잠금이 풀리는 시각이 되면 잠금이 풀린다")
    void lockIsReleasedAtUnlockInstant() {
        List<LoginFailure> failures = failuresMinutesAgo(0, 0, 0, 0, 0);
        Instant lockedUntil = LoginLockout.lockedUntil(failures, IP, now(clock)).orElseThrow();

        clock.setInstant(lockedUntil.minusSeconds(1));
        assertThat(LoginLockout.lockedUntil(failures, IP, now(clock))).isPresent();

        clock.setInstant(lockedUntil);
        assertThat(LoginLockout.lockedUntil(failures, IP, now(clock))).isEmpty();
    }

    @Test
    @DisplayName("[PW-03] 실패가 5건을 넘으면 구간 안 가장 최근 5건 중 가장 오래된 실패를 기준으로 잠금이 풀린다")
    void moreThanFiveFailuresUseOldestOfLatestFive() {
        List<LoginFailure> failures = failuresMinutesAgo(9, 8, 6, 4, 3, 2, 1);

        Optional<Instant> lockedUntil = LoginLockout.lockedUntil(failures, IP, now(clock));

        assertThat(lockedUntil).contains(now(clock).minus(Duration.ofMinutes(6)).plus(LoginLockout.WINDOW));
    }

    @Test
    @DisplayName("[PW-03] 10분이 지난 실패는 세지 않는다")
    void failuresOlderThanWindowAreIgnored() {
        List<LoginFailure> failures = failuresMinutesAgo(30, 20, 15, 3, 1);

        Optional<Instant> lockedUntil = LoginLockout.lockedUntil(failures, IP, now(clock));

        assertThat(lockedUntil).isEmpty();
    }

    @Test
    @DisplayName("[PW-03] 실패 시각을 어떤 순서로 넘겨도 같은 결과를 낸다")
    void failureOrderDoesNotMatter() {
        List<LoginFailure> failures = failuresMinutesAgo(1, 9, 3, 7, 5);

        Optional<Instant> lockedUntil = LoginLockout.lockedUntil(failures, IP, now(clock));

        assertThat(lockedUntil).contains(now(clock).minus(Duration.ofMinutes(9)).plus(LoginLockout.WINDOW));
    }

    @Test
    @DisplayName("[PW-03] 실패가 없으면 잠그지 않는다")
    void noFailuresDoNotLock() {
        assertThat(LoginLockout.lockedUntil(List.of(), IP, now(clock))).isEmpty();
    }

    @Test
    @DisplayName("[PW-03] 다른 IP의 실패는 이 IP의 5번에 넣지 않는다")
    void otherIpFailuresDoNotLockThisIp() {
        List<LoginFailure> failures = failuresMinutesAgoFrom(OTHER_IP, 9, 7, 5, 3, 1);
        failures.addAll(failuresMinutesAgo(2, 1, 1, 0));

        assertThat(LoginLockout.lockedUntil(failures, IP, now(clock))).isEmpty();
        assertThat(LoginLockout.lockedUntil(failures, OTHER_IP, now(clock)))
                .contains(now(clock).minus(Duration.ofMinutes(9)).plus(LoginLockout.WINDOW));
    }

    @Test
    @DisplayName("[PW-03] 여러 IP에서 합쳐 50번 실패하면 실패가 없는 IP는 막지 않는다")
    void fiftyAccountFailuresDoNotLockOtherIp() {
        List<LoginFailure> failures = failuresFromManyIps(LoginLockout.ACCOUNT_MAX_FAILURES);

        assertThat(LoginLockout.lockedUntil(failures, IP, now(clock))).isEmpty();
    }

    @Test
    @DisplayName("[PW-03] 여러 IP에서 합쳐 51번 실패하면 모든 IP를 막고, 최근 51건 중 가장 오래된 실패로부터 10분 뒤에 푼다")
    void moreThanFiftyAccountFailuresLockEveryIp() {
        List<LoginFailure> failures = failuresFromManyIps(LoginLockout.ACCOUNT_MAX_FAILURES + 1);
        failures.add(new LoginFailure(now(clock).minus(Duration.ofMinutes(9)), "203.0.113.250"));

        Optional<Instant> lockedUntil = LoginLockout.lockedUntil(failures, IP, now(clock));

        assertThat(lockedUntil)
                .contains(now(clock)
                        .minusSeconds(LoginLockout.ACCOUNT_MAX_FAILURES + 1)
                        .plus(LoginLockout.WINDOW));
    }

    @Test
    @DisplayName("[PW-03] IP 잠금과 회원 잠금이 함께 걸리면 늦게 풀리는 쪽을 따른다")
    void laterOfIpAndAccountLockWins() {
        List<LoginFailure> failures = failuresFromManyIps(LoginLockout.ACCOUNT_MAX_FAILURES + 1);
        failures.addAll(failuresMinutesAgo(9, 8, 7, 6, 5));

        Optional<Instant> lockedUntil = LoginLockout.lockedUntil(failures, IP, now(clock));

        assertThat(lockedUntil)
                .contains(now(clock)
                        .minusSeconds(LoginLockout.ACCOUNT_MAX_FAILURES + 1)
                        .plus(LoginLockout.WINDOW));
    }

    private static Instant now(Clock clock) {
        return Instant.now(clock);
    }

    private List<LoginFailure> failuresMinutesAgo(long... minutesAgo) {
        return failuresMinutesAgoFrom(IP, minutesAgo);
    }

    private List<LoginFailure> failuresMinutesAgoFrom(String ip, long... minutesAgo) {
        List<LoginFailure> failures = new ArrayList<>();
        for (long minutes : minutesAgo) {
            failures.add(new LoginFailure(now(clock).minus(Duration.ofMinutes(minutes)), ip));
        }
        return failures;
    }

    private List<LoginFailure> failuresFromManyIps(int count) {
        List<LoginFailure> failures = new ArrayList<>();
        for (int i = 1; i <= count; i++) {
            failures.add(new LoginFailure(now(clock).minusSeconds(i), "203.0.113." + (i % 10)));
        }
        return failures;
    }
}
