package com.moewadeul.basecamp.application.lifecycle;

import static org.assertj.core.api.Assertions.assertThat;

import com.moewadeul.basecamp.domain.BasecampErrorCode;
import com.moewadeul.common.error.BusinessException;
import com.moewadeul.common.testsupport.IntegrationTest;
import com.moewadeul.common.testsupport.TestSequence;
import com.moewadeul.member.infra.MemberJpaRepository;
import com.moewadeul.trust.application.CompanionReviewFixture;
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
class BasecampOpenConcurrencyIntegrationTest {

    private static final int REPETITIONS = 5;
    private static final int OPEN_LIMIT = 3;
    private static final LocalDate FIRST_START = LocalDate.of(2026, 10, 20);

    @Autowired
    private BasecampOpenService basecampOpenService;

    @Autowired
    private MemberJpaRepository memberRepository;

    @Autowired
    private JdbcTemplate jdbc;

    private long leaderId;
    private long spotId;

    @BeforeEach
    void setUp() {
        leaderId = new CompanionReviewFixture(jdbc, memberRepository).saveVerifiedMember(TestSequence.nickname());
        jdbc.update("INSERT INTO spot (type, name, location, weather_nx, weather_ny, status, created_at, updated_at)"
                + " VALUES ('BAKJI', '개머리언덕', ST_GeomFromText('POINT(37.25 127.25)', 4326), 60, 127, 'ACTIVE',"
                + " NOW(6), NOW(6))");
        spotId = jdbc.queryForObject("SELECT MAX(id) FROM spot", Long.class);
    }

    @RepeatedTest(REPETITIONS)
    @DisplayName("[BC-01][NFR-10] 베이스캠프 2개를 연 회원이 열기 요청 2건을 동시에 보내면 1건만 열리고 나머지는 BASECAMP_OPEN_LIMIT이다")
    void onlyOneOfTwoConcurrentOpensSucceedsAtLimit() throws Exception {
        openSequentially(OPEN_LIMIT - 1);

        List<Throwable> failures = openConcurrently(2);

        assertThat(openCount()).isEqualTo(OPEN_LIMIT);
        assertOnlyOpenLimitFailures(failures, 1);
    }

    @RepeatedTest(REPETITIONS)
    @DisplayName("[BC-01][NFR-10] 베이스캠프 2개를 연 회원이 열기 요청 5건을 동시에 보내면 1건만 열리고 나머지는 BASECAMP_OPEN_LIMIT이다")
    void onlyOneOfFiveConcurrentOpensSucceedsAtLimit() throws Exception {
        openSequentially(OPEN_LIMIT - 1);

        List<Throwable> failures = openConcurrently(5);

        assertThat(openCount()).isEqualTo(OPEN_LIMIT);
        assertOnlyOpenLimitFailures(failures, 4);
    }

    @RepeatedTest(REPETITIONS)
    @DisplayName("[BC-01][NFR-10] 베이스캠프가 없는 회원이 열기 요청 5건을 동시에 보내면 3건만 열린다")
    void onlyThreeOfFiveConcurrentOpensSucceedFromZero() throws Exception {
        List<Throwable> failures = openConcurrently(5);

        assertThat(openCount()).isEqualTo(OPEN_LIMIT);
        assertOnlyOpenLimitFailures(failures, 2);
    }

    private void openSequentially(int count) {
        for (int i = 0; i < count; i++) {
            open(i);
        }
    }

    private BasecampOpenResult open(int index) {
        LocalDate start = FIRST_START.plusDays(index * 3L);
        return basecampOpenService.open(
                leaderId, new BasecampOpenCommand(spotId, "굴업도 1박", "함께 가요", start, start.plusDays(1), 4, null));
    }

    private int openCount() {
        return jdbc.queryForObject(
                "SELECT COUNT(*) FROM basecamp WHERE leader_id = ? AND status IN ('RECRUITING', 'CLOSED')",
                Integer.class,
                leaderId);
    }

    private static void assertOnlyOpenLimitFailures(List<Throwable> failures, int expected) {
        assertThat(failures).hasSize(expected);
        assertThat(failures)
                .allSatisfy(failure -> assertThat(failure)
                        .isInstanceOfSatisfying(
                                BusinessException.class,
                                e -> assertThat(e.getErrorCode()).isEqualTo(BasecampErrorCode.BASECAMP_OPEN_LIMIT)));
    }

    // 같은 캠프 리더의 열기 요청을 스레드마다 하나씩 동시에 출발시키고, 던져진 예외를 모아 돌려준다.
    // 출발일은 요청마다 달라서 일정 검사로는 막히지 않는다.
    private List<Throwable> openConcurrently(int threads) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<BasecampOpenResult>> futures = new ArrayList<>();
            for (int i = 0; i < threads; i++) {
                int index = OPEN_LIMIT + i;
                Callable<BasecampOpenResult> task = () -> {
                    ready.countDown();
                    start.await();
                    return open(index);
                };
                futures.add(executor.submit(task));
            }
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            return collectFailures(futures);
        } finally {
            executor.shutdown();
            assertThat(executor.awaitTermination(30, TimeUnit.SECONDS)).isTrue();
        }
    }

    private static <T> List<Throwable> collectFailures(List<Future<T>> futures) throws InterruptedException {
        List<Throwable> failures = new ArrayList<>();
        for (Future<T> future : futures) {
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
