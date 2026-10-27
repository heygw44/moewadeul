package com.moewadeul.community.application.post;

import static com.moewadeul.member.domain.MemberBuilder.aMember;
import static org.assertj.core.api.Assertions.assertThat;

import com.moewadeul.common.testsupport.IntegrationTest;
import com.moewadeul.member.infra.MemberJpaRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

@IntegrationTest
class CommunityPostViewCountConcurrencyIntegrationTest {

    private static final int MEMBERS = 15;
    private static final int ANONYMOUS_VIEWS = 5;
    private static final int OPENS_PER_VIEWER = 3;

    @Autowired
    private CommunityPostQueryService queryService;

    @Autowired
    private CommunityPostCommandService postCommandService;

    @Autowired
    private MemberJpaRepository memberRepository;

    @Autowired
    private CommunityViewCountFlusher viewCountFlusher;

    @Autowired
    private JdbcTemplate jdbc;

    @RepeatedTest(5)
    @DisplayName("[F-29][CM-11] 회원 15명과 비회원 5명이 같은 글의 상세를 동시에 3번씩 열면 조회수는 정확히 60이다")
    void concurrentViewsAreAllCounted() throws Exception {
        // given
        long authorId = saveMember();
        long postId = postCommandService.write(authorId, new CommunityPostWriteCommand("제목", "본문", null));
        List<Long> viewerIds = new ArrayList<>();
        for (int i = 0; i < MEMBERS; i++) {
            viewerIds.add(saveMember());
        }
        for (int i = 0; i < ANONYMOUS_VIEWS; i++) {
            viewerIds.add(null);
        }

        // when
        List<Throwable> failures = runConcurrently(viewerIds, viewerId -> {
            for (int i = 0; i < OPENS_PER_VIEWER; i++) {
                queryService.detail(postId, viewerId);
            }
        });

        // then
        assertThat(failures).isEmpty();
        viewCountFlusher.flush();
        assertThat(viewCount(postId)).isEqualTo(viewerIds.size() * OPENS_PER_VIEWER);
    }

    @Test
    @DisplayName("[F-29][CM-11] DB에 더하기 전에는 상세 응답에 아직 더하지 않은 조회가 보이고, 더하는 사이 숨겨진 글에는 더하지 않는다")
    void pendingViewsAppearInDetailAndHiddenPostIsNotUpdated() {
        // given
        long authorId = saveMember();
        long postId = postCommandService.write(authorId, new CommunityPostWriteCommand("제목", "본문", null));
        queryService.detail(postId, null);

        // when
        long secondView = queryService.detail(postId, null).viewCount();
        jdbc.update("UPDATE community_post SET status = 'HIDDEN' WHERE id = ?", postId);
        viewCountFlusher.flush();

        // then
        assertThat(secondView).isEqualTo(2);
        assertThat(viewCount(postId)).isZero();
    }

    private long saveMember() {
        return memberRepository.saveAndFlush(aMember().build()).getId();
    }

    private int viewCount(long postId) {
        return jdbc.queryForObject("SELECT view_count FROM community_post WHERE id = ?", Integer.class, postId);
    }

    private List<Throwable> runConcurrently(List<Long> viewerIds, ViewerAction action) throws Exception {
        int threads = viewerIds.size();
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<?>> futures = new ArrayList<>();
            for (Long viewerId : viewerIds) {
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    start.await();
                    action.run(viewerId);
                    return null;
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

    private List<Throwable> collectFailures(List<Future<?>> futures) throws InterruptedException {
        List<Throwable> failures = new ArrayList<>();
        for (Future<?> future : futures) {
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
    private interface ViewerAction {
        void run(Long viewerId) throws Exception;
    }
}
