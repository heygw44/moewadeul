package com.moewadeul.trip.application;

import static com.moewadeul.member.domain.MemberBuilder.aMember;
import static org.assertj.core.api.Assertions.assertThat;

import com.moewadeul.common.error.BusinessException;
import com.moewadeul.common.error.ErrorCode;
import com.moewadeul.common.testsupport.IntegrationTest;
import com.moewadeul.common.testsupport.MutableClock;
import com.moewadeul.member.infra.MemberJpaRepository;
import com.moewadeul.spot.domain.GeoPoint;
import com.moewadeul.spot.domain.ParkAreaJudgement;
import com.moewadeul.spot.domain.Spot;
import com.moewadeul.spot.infra.SpotJpaRepository;
import com.moewadeul.trip.domain.TripErrorCode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.RepeatedTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

@IntegrationTest
class SoloTripRegisterConcurrencyIntegrationTest {

    private static final int REPETITIONS = 5;
    private static final int UPCOMING_LIMIT = 3;

    @Autowired
    private SoloTripCommandService commandService;

    @Autowired
    private SpotJpaRepository spotRepository;

    @Autowired
    private MemberJpaRepository memberRepository;

    @Autowired
    private JdbcTemplate jdbc;

    private long memberId;
    private long spotId;

    @BeforeEach
    void setUp() {
        memberId = memberRepository.saveAndFlush(aMember().build()).getId();
        Spot spot = Spot.bakji(
                "능선 끝 평지",
                new GeoPoint(37.25, 127.25),
                ParkAreaJudgement.outside(MutableClock.DEFAULT_INSTANT),
                MutableClock.DEFAULT_INSTANT);
        spotId = spotRepository.save(spot).getId();
    }

    @RepeatedTest(REPETITIONS)
    @DisplayName("[F-23][ST-03] 일정 2개를 가진 회원이 겹치지 않는 일정 2건을 동시에 등록하면 1건만 저장되고 나머지는 SOLO_TRIP_LIMIT이다")
    void onlyOneOfTwoConcurrentRegistrationsSucceedsAtLimit() throws Exception {
        register("2026-10-10", "2026-10-11");
        register("2026-10-13", "2026-10-14");

        List<Throwable> failures = runConcurrently(
                List.of(() -> register("2026-10-16", "2026-10-17"), () -> register("2026-10-19", "2026-10-20")));

        assertThat(tripCount()).isEqualTo(UPCOMING_LIMIT);
        assertFailures(failures, TripErrorCode.SOLO_TRIP_LIMIT);
    }

    @RepeatedTest(REPETITIONS)
    @DisplayName("[F-23][ST-03] 일정이 없는 회원이 밤이 겹치는 일정 2건을 동시에 등록하면 1건만 저장되고 나머지는 SOLO_TRIP_OVERLAP이다")
    void onlyOneOfTwoConcurrentOverlappingRegistrationsSucceeds() throws Exception {
        List<Throwable> failures = runConcurrently(
                List.of(() -> register("2026-10-10", "2026-10-12"), () -> register("2026-10-11", "2026-10-13")));

        assertThat(tripCount()).isEqualTo(1);
        assertFailures(failures, TripErrorCode.SOLO_TRIP_OVERLAP);
    }

    private long register(String start, String end) {
        return commandService.register(
                memberId, new SoloTripRegisterCommand(spotId, LocalDate.parse(start), LocalDate.parse(end)));
    }

    private int tripCount() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM solo_trip WHERE member_id = ?", Integer.class, memberId);
    }

    private static void assertFailures(List<Throwable> failures, ErrorCode expected) {
        assertThat(failures).hasSize(1);
        assertThat(failures.getFirst())
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(expected));
    }

    private static List<Throwable> runConcurrently(List<Callable<Long>> actions) throws Exception {
        int threads = actions.size();
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<Long>> futures = new ArrayList<>();
            for (Callable<Long> action : actions) {
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    start.await();
                    return action.call();
                }));
            }
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            return collectFailures(futures);
        } finally {
            executor.shutdown();
            assertThat(executor.awaitTermination(30, TimeUnit.SECONDS)).isTrue();
        }
    }

    private static List<Throwable> collectFailures(List<Future<Long>> futures) throws InterruptedException {
        List<Throwable> failures = new ArrayList<>();
        for (Future<Long> future : futures) {
            try {
                future.get(30, TimeUnit.SECONDS);
            } catch (ExecutionException e) {
                failures.add(e.getCause());
            } catch (TimeoutException e) {
                failures.add(e);
            }
        }
        return failures;
    }
}
