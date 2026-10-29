package com.moewadeul.memory.application.image;

import static com.moewadeul.member.domain.MemberBuilder.aMember;
import static org.assertj.core.api.Assertions.assertThat;

import com.moewadeul.common.error.CommonErrorCode;
import com.moewadeul.common.error.RateLimitedException;
import com.moewadeul.common.image.ImageUploadCommand;
import com.moewadeul.common.testsupport.IntegrationTest;
import com.moewadeul.member.infra.MemberJpaRepository;
import com.moewadeul.memory.domain.image.MemoryImage;
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
class MemoryImageUploadConcurrencyIntegrationTest {

    private static final int THREADS = 4;
    private static final ImageUploadCommand COMMAND = new ImageUploadCommand("image/jpeg", 1000L, null);

    @Autowired
    private MemoryImageUploadService uploadService;

    @Autowired
    private MemberJpaRepository memberRepository;

    @Autowired
    private JdbcTemplate jdbc;

    @RepeatedTest(5)
    @DisplayName("[F-33][MM-05] 상한에 한 장 모자랄 때 같은 회원이 동시에 발급을 요청하면 하나만 성공하고 나머지는 IMAGE_UPLOAD_LIMITED다")
    void concurrentIssuesAtLimitSucceedOnce() throws Exception {
        // given
        long memberId = memberRepository.saveAndFlush(aMember().build()).getId();
        for (int i = 1; i < MemoryImage.MAX_UNATTACHED_PER_MEMBER; i++) {
            uploadService.issue(memberId, COMMAND);
        }
        List<Callable<Object>> tasks = new ArrayList<>();
        for (int i = 0; i < THREADS; i++) {
            tasks.add(() -> uploadService.issue(memberId, COMMAND));
        }

        // when
        List<Throwable> failures = runConcurrently(tasks);

        // then
        assertThat(failures)
                .hasSize(THREADS - 1)
                .allSatisfy(failure -> assertThat(failure)
                        .isInstanceOfSatisfying(
                                RateLimitedException.class,
                                e -> assertThat(e.getErrorCode()).isEqualTo(CommonErrorCode.IMAGE_UPLOAD_LIMITED)));
        assertThat(jdbc.queryForObject(
                        "SELECT COUNT(*) FROM memory_image WHERE member_id = ?", Integer.class, memberId))
                .isEqualTo(MemoryImage.MAX_UNATTACHED_PER_MEMBER);
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
