package com.moewadeul.memory.application.like;

import static com.moewadeul.member.domain.MemberBuilder.aMember;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.moewadeul.common.error.BusinessException;
import com.moewadeul.common.error.CommonErrorCode;
import com.moewadeul.common.testsupport.IntegrationTest;
import com.moewadeul.common.testsupport.LockWaits;
import com.moewadeul.member.infra.MemberJpaRepository;
import com.moewadeul.memory.application.MemoryCommandService;
import com.moewadeul.memory.application.MemoryWriteCommand;
import com.moewadeul.memory.infra.like.MemoryLikeMapper;
import java.sql.Connection;
import java.sql.DriverManager;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
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
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.mysql.MySQLContainer;

@IntegrationTest
class MemoryLikeConcurrencyIntegrationTest {

    private static final int THREADS = 20;
    private static final int SAME_MEMBER_PRESSES = 10;

    @Autowired
    private MemoryLikeService likeService;

    @Autowired
    private MemoryCommandService memoryCommandService;

    @Autowired
    private MemoryLikeMapper likeMapper;

    @Autowired
    private MemberJpaRepository memberRepository;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private MySQLContainer mysql;

    @RepeatedTest(5)
    @DisplayName("[F-33][MM-09] 서로 다른 회원 20명이 같은 기록에 동시에 좋아요를 누르면 예외 없이 20건이 저장되고 좋아요 수도 20이다")
    void concurrentLikesFromDifferentMembersAreAllCounted() throws Exception {
        // given
        List<Long> memberIds = saveMembers(THREADS);
        long memoryId = saveMemory(memberIds.get(0));

        // when
        List<Throwable> failures = runConcurrently(memberIds, memberId -> likeService.like(memberId, memoryId));

        // then
        assertThat(failures).isEmpty();
        assertThat(rowCount(memoryId)).isEqualTo(THREADS);
        assertThat(likeMapper.countByMemory(memoryId)).isEqualTo(THREADS);
    }

    @RepeatedTest(5)
    @DisplayName("[F-33][MM-09] 같은 회원이 같은 기록에 동시에 10번 좋아요를 눌러도 예외 없이 1건만 저장된다")
    void sameMemberLikingConcurrentlyLeavesOneRow() throws Exception {
        // given
        long memberId = saveMembers(1).get(0);
        long memoryId = saveMemory(memberId);

        // when
        List<Throwable> failures = runConcurrently(
                Collections.nCopies(SAME_MEMBER_PRESSES, memberId), id -> likeService.like(id, memoryId));

        // then
        assertThat(failures).isEmpty();
        assertThat(rowCount(memoryId)).isEqualTo(1);
        assertThat(likeMapper.countByMemory(memoryId)).isEqualTo(1);
    }

    @RepeatedTest(5)
    @DisplayName("[F-33][MM-09] 관리자가 기록을 숨기는 트랜잭션이 끝나기 전에 들어온 좋아요는 숨김이 커밋된 뒤 404로 거절되고 행을 남기지 않는다")
    void likeArrivingWhileMemoryIsBeingHiddenIsRejected() throws Exception {
        // given
        long memberId = saveMembers(1).get(0);
        long memoryId = saveMemory(memberId);
        CountDownLatch hidden = new CountDownLatch(1);
        CountDownLatch commit = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try (Connection monitor = DriverManager.getConnection(mysql.getJdbcUrl(), "root", mysql.getPassword())) {
            Future<?> hide = executor.submit(() -> transactionTemplate.executeWithoutResult(status -> {
                jdbc.update("UPDATE memory SET status = 'HIDDEN' WHERE id = ?", memoryId);
                hidden.countDown();
                await(commit);
            }));
            assertThat(hidden.await(10, TimeUnit.SECONDS)).isTrue();

            // when
            Future<MemoryLikeResult> like = executor.submit(() -> likeService.like(memberId, memoryId));
            LockWaits.await(monitor, 1, "숨김 트랜잭션이 잡은 기록 행 잠금을 좋아요가 기다린다");
            commit.countDown();
            hide.get(10, TimeUnit.SECONDS);

            // then
            assertThatThrownBy(() -> like.get(10, TimeUnit.SECONDS))
                    .cause()
                    .isInstanceOfSatisfying(
                            BusinessException.class,
                            e -> assertThat(e.getErrorCode()).isEqualTo(CommonErrorCode.NOT_FOUND));
            assertThat(rowCount(memoryId)).isZero();
        } finally {
            commit.countDown();
            executor.shutdown();
            assertThat(executor.awaitTermination(30, TimeUnit.SECONDS)).isTrue();
        }
    }

    private static void await(CountDownLatch latch) {
        try {
            assertThat(latch.await(10, TimeUnit.SECONDS)).isTrue();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    private long saveMemory(long authorId) {
        return memoryCommandService.write(
                authorId,
                new MemoryWriteCommand(null, 37.25, 127.25, LocalDate.of(2026, 10, 4), 1, null, "PUBLIC", List.of()));
    }

    private List<Long> saveMembers(int count) {
        List<Long> ids = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            ids.add(memberRepository.saveAndFlush(aMember().build()).getId());
        }
        return ids;
    }

    private int rowCount(long memoryId) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM memory_like WHERE memory_id = ?", Integer.class, memoryId);
    }

    private <T> List<Throwable> runConcurrently(List<Long> memberIds, MemberAction<T> action) throws Exception {
        int threads = memberIds.size();
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<T>> futures = new ArrayList<>();
            for (long memberId : memberIds) {
                Callable<T> task = () -> {
                    ready.countDown();
                    start.await();
                    return action.run(memberId);
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

    private <T> List<Throwable> collectFailures(List<Future<T>> futures) throws InterruptedException {
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

    @FunctionalInterface
    private interface MemberAction<T> {
        T run(long memberId) throws Exception;
    }
}
