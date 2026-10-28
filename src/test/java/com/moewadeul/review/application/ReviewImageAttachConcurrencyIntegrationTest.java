package com.moewadeul.review.application;

import static com.moewadeul.member.domain.MemberBuilder.aMember;
import static org.assertj.core.api.Assertions.assertThat;

import com.moewadeul.common.error.InvalidFieldException;
import com.moewadeul.common.testsupport.IntegrationTest;
import com.moewadeul.common.testsupport.MutableClock;
import com.moewadeul.common.testsupport.TestImageStorage;
import com.moewadeul.member.infra.MemberJpaRepository;
import com.moewadeul.spot.domain.GeoPoint;
import com.moewadeul.spot.domain.ParkAreaJudgement;
import com.moewadeul.spot.domain.Spot;
import com.moewadeul.spot.infra.SpotJpaRepository;
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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.RepeatedTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

@IntegrationTest
class ReviewImageAttachConcurrencyIntegrationTest {

    private static final int THREADS = 4;
    private static final LocalDate YESTERDAY = LocalDate.of(2026, 10, 4);

    @Autowired
    private ReviewImageUploadService uploadService;

    @Autowired
    private SpotReviewCommandService commandService;

    @Autowired
    private MemberJpaRepository memberRepository;

    @Autowired
    private SpotJpaRepository spotRepository;

    @Autowired
    private TestImageStorage storage;

    @Autowired
    private JdbcTemplate jdbc;

    @RepeatedTest(5)
    @DisplayName("[F-24][RI-02] 올린 이미지 하나를 후기 여러 개에 동시에 붙이면 하나만 성공하고 나머지는 imageIds 오류이며, 이미지는 후기 하나에만 붙는다")
    void sameImageAttachedByConcurrentReviewsSucceedsOnce() throws Exception {
        // given
        long memberId = memberRepository.saveAndFlush(aMember().build()).getId();
        long spotId = spotRepository
                .save(Spot.bakji(
                        "능선 끝 평지",
                        new GeoPoint(37.25, 127.25),
                        ParkAreaJudgement.outside(MutableClock.DEFAULT_INSTANT),
                        MutableClock.DEFAULT_INSTANT))
                .getId();
        long imageId = uploadService.issue(memberId, "image/jpeg", 1000L).imageId();
        storage.markUploaded(
                jdbc.queryForObject("SELECT object_key FROM review_image WHERE id = ?", String.class, imageId), 1000L);
        List<Callable<Object>> tasks = new ArrayList<>();
        for (int i = 0; i < THREADS; i++) {
            LocalDate visitedDate = YESTERDAY.minusDays(i);
            tasks.add(() -> commandService.write(
                    memberId, spotId, new SpotReviewWriteCommand(visitedDate, 4, "좋았다", List.of(imageId))));
        }

        // when
        List<Throwable> failures = runConcurrently(tasks);

        // then
        assertThat(failures)
                .hasSize(THREADS - 1)
                .allSatisfy(failure -> assertThat(failure).isInstanceOf(InvalidFieldException.class));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM spot_review", Integer.class))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT review_id FROM review_image WHERE id = ?", Long.class, imageId))
                .isEqualTo(jdbc.queryForObject("SELECT id FROM spot_review", Long.class));
    }

    private List<Throwable> runConcurrently(List<Callable<Object>> tasks) throws Exception {
        int threads = tasks.size();
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<Object>> futures = new ArrayList<>();
            for (Callable<Object> task : tasks) {
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    start.await();
                    return task.call();
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

    private List<Throwable> collectFailures(List<Future<Object>> futures) throws InterruptedException {
        List<Throwable> failures = new ArrayList<>();
        for (Future<Object> future : futures) {
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
