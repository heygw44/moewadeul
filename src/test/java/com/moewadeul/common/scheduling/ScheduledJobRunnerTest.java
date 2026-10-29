package com.moewadeul.common.scheduling;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

class ScheduledJobRunnerTest {

    private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
    private final ScheduledJobRunner runner = new ScheduledJobRunner(new ScheduledJobMetrics(meterRegistry));
    private final Logger runnerLogger = (Logger) LoggerFactory.getLogger(ScheduledJobRunner.class);
    private final ListAppender<ILoggingEvent> logs = new ListAppender<>();

    @BeforeEach
    void attachAppender() {
        logs.start();
        runnerLogger.addAppender(logs);
    }

    @AfterEach
    void detachAppender() {
        runnerLogger.detachAppender(logs);
    }

    @Test
    @DisplayName("[NFR-09] 작업이 예외로 끝나면 예외를 삼키고, 작업 이름으로 만든 문구를 error로 남기고, 실패 수를 1 올린다")
    void swallowsExceptionAndRecordsFailure() {
        assertThatCode(() -> runner.run("review-image-cleanup", () -> {
                    throw new IllegalStateException("DB 연결 실패");
                }))
                .doesNotThrowAnyException();

        assertThat(failures("review-image-cleanup")).isEqualTo(1);
        assertThat(logs.list).singleElement().satisfies(event -> {
            assertThat(event.getLevel()).isEqualTo(Level.ERROR);
            assertThat(event.getFormattedMessage()).isEqualTo("review image cleanup failed");
            assertThat(event.getThrowableProxy().getMessage()).isEqualTo("DB 연결 실패");
        });
    }

    @Test
    @DisplayName("[NFR-09] 작업이 정상으로 끝나면 로그도 실패 수도 남기지 않는다")
    void successLeavesNoFailure() {
        AtomicInteger calls = new AtomicInteger();

        runner.register("login-history-cleanup");
        runner.run("login-history-cleanup", calls::incrementAndGet);

        assertThat(calls).hasValue(1);
        assertThat(failures("login-history-cleanup")).isZero();
        assertThat(logs.list).isEmpty();
    }

    @Test
    @DisplayName("[NFR-09] 등록만 해 두면 실패가 없어도 실패 수 시계열이 0으로 먼저 생긴다")
    void registerCreatesZeroSeries() {
        runner.register("sanction-expiry");

        assertThat(failures("sanction-expiry")).isZero();
    }

    @Test
    @DisplayName("[NFR-09] 건수를 돌려주는 작업이 예외로 끝나면 예외를 삼키고 로그를 남기고 실패 수를 1 올린다")
    void countingRunSwallowsException() {
        assertThatCode(() -> runner.runCountingFailures("program-payment-expiry", () -> {
                    throw new IllegalStateException("DB 연결 실패");
                }))
                .doesNotThrowAnyException();

        assertThat(failures("program-payment-expiry")).isEqualTo(1);
        assertThat(logs.list)
                .singleElement()
                .satisfies(event -> assertThat(event.getFormattedMessage()).isEqualTo("program payment expiry failed"));
    }

    @Test
    @DisplayName("[NFR-09] 건수를 돌려주는 작업이 항목 실패를 여러 건 알려도 로그 없이 실패 수를 1만 올린다")
    void countingRunRecordsOnceWhenItemsFailed() {
        runner.runCountingFailures("basecamp-auto-transition", () -> 3);

        assertThat(failures("basecamp-auto-transition")).isEqualTo(1);
        assertThat(logs.list).isEmpty();
    }

    @Test
    @DisplayName("[NFR-09] 건수를 돌려주는 작업이 실패 0건이면 실패 수를 올리지 않는다")
    void countingRunIgnoresZeroFailures() {
        runner.register("basecamp-auto-transition");

        runner.runCountingFailures("basecamp-auto-transition", () -> 0);

        assertThat(failures("basecamp-auto-transition")).isZero();
        assertThat(logs.list).isEmpty();
    }

    private double failures(String job) {
        return meterRegistry
                .get("moewadeul.scheduled.job.failures")
                .tag("job", job)
                .counter()
                .count();
    }
}
