package com.moewadeul.community.application.post;

import static com.moewadeul.member.domain.MemberBuilder.aMember;
import static org.assertj.core.api.Assertions.assertThat;

import com.moewadeul.common.moderation.ContentReportReason;
import com.moewadeul.common.testsupport.IntegrationTest;
import com.moewadeul.common.testsupport.TestImageStorage;
import com.moewadeul.common.web.PatchField;
import com.moewadeul.community.application.comment.CommunityCommentCommandService;
import com.moewadeul.community.application.comment.CommunityCommentWriteCommand;
import com.moewadeul.community.application.image.CommunityImageUploadCommand;
import com.moewadeul.community.application.image.CommunityImageUploadService;
import com.moewadeul.community.application.moderation.CommunityModerationService;
import com.moewadeul.community.application.report.CommunityReportCommand;
import com.moewadeul.community.application.report.CommunityReportService;
import com.moewadeul.community.domain.comment.CommunityCommentRepository;
import com.moewadeul.community.domain.image.CommunityImageRepository;
import com.moewadeul.community.domain.post.CommunityPostRepository;
import com.moewadeul.member.infra.MemberJpaRepository;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.RepeatedTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;

@IntegrationTest
class CommunityAuthorEditConcurrencyIntegrationTest {

    private static final int REPETITIONS = 3;
    private static final long MIDDLE_WAIT_SECONDS = 2;
    private static final long IMAGE_SIZE = 1000L;
    private static final CommunityReportCommand REPORT = new CommunityReportCommand(ContentReportReason.SPAM, "광고입니다");
    private static final Set<String> POST_READ =
            Set.of("CommunityPostRepository.findById", "CommunityPostRepository.findByIdForUpdate");
    private static final Set<String> COMMENT_READ =
            Set.of("CommunityCommentRepository.findById", "CommunityCommentRepository.findByIdForUpdate");
    private static final Set<String> CURRENT_IMAGES_READ = Set.of("CommunityImageRepository.findIdsByPostId");

    @Autowired
    private CommunityPostCommandService postCommandService;

    @Autowired
    private CommunityCommentCommandService commentCommandService;

    @Autowired
    private CommunityModerationService moderationService;

    @Autowired
    private CommunityReportService reportService;

    @Autowired
    private CommunityImageUploadService uploadService;

    @Autowired
    private MemberJpaRepository memberRepository;

    @Autowired
    private TestImageStorage storage;

    @Autowired
    private JdbcTemplate jdbc;

    private final CountDownLatch paused = new CountDownLatch(1);
    private final CountDownLatch resume = new CountDownLatch(1);
    private final AtomicBoolean pauseArmed = new AtomicBoolean();
    private ExecutorService executor;
    private long authorId;

    @BeforeEach
    void setUp() {
        executor = Executors.newFixedThreadPool(2);
        authorId = saveMember();
    }

    @AfterEach
    void tearDown() throws InterruptedException {
        PauseHooks.clear();
        resume.countDown();
        executor.shutdown();
        assertThat(executor.awaitTermination(60, TimeUnit.SECONDS)).isTrue();
    }

    @RepeatedTest(REPETITIONS)
    @DisplayName("[F-29][CM-08] 작성자가 글을 고치는 사이 관리자가 글을 숨기면, 글은 HIDDEN으로 남는다")
    void postReviseDuringHideKeepsHidden() throws Exception {
        long postId = savePost();
        long adminId = saveMember();
        pauseAfter(POST_READ);

        Future<?> revise = executor.submit(() -> postCommandService.revise(authorId, postId, retitle("새 제목")));
        awaitPaused();
        assertThat(runInMiddle(() -> moderationService.hidePost(postId, adminId), revise))
                .isNull();

        assertThat(postStatusOf(postId)).isEqualTo("HIDDEN");
        assertThat(jdbc.queryForObject("SELECT title FROM community_post WHERE id = ?", String.class, postId))
                .isEqualTo("새 제목");
    }

    @RepeatedTest(REPETITIONS)
    @DisplayName("[F-29][CM-07] 작성자가 글을 고치는 사이 다섯 번째 신고가 들어오면, 글은 검토 대기로 남는다")
    void postReviseDuringFifthReportKeepsPendingReview() throws Exception {
        long postId = savePost();
        for (int i = 0; i < 4; i++) {
            reportService.reportPost(saveMember(), postId, REPORT);
        }
        long fifthReporter = saveMember();
        pauseAfter(POST_READ);

        Future<?> revise = executor.submit(() -> postCommandService.revise(authorId, postId, retitle("새 제목")));
        awaitPaused();
        assertThat(runInMiddle(
                        () -> {
                            reportService.reportPost(fifthReporter, postId, REPORT);
                            return null;
                        },
                        revise))
                .isNull();

        assertThat(postStatusOf(postId)).isEqualTo("PENDING_REVIEW");
    }

    @RepeatedTest(REPETITIONS)
    @DisplayName("[F-29][CM-08] 작성자가 댓글을 고치는 사이 관리자가 댓글을 숨기면, 댓글은 HIDDEN으로 남는다")
    void commentReviseDuringHideKeepsHidden() throws Exception {
        long commentId = saveComment(savePost());
        long adminId = saveMember();
        pauseAfter(COMMENT_READ);

        Future<?> revise = executor.submit(() -> commentCommandService.revise(authorId, commentId, "고친 댓글"));
        awaitPaused();
        assertThat(runInMiddle(() -> moderationService.hideComment(commentId, adminId), revise))
                .isNull();

        assertThat(commentStatusOf(commentId)).isEqualTo("HIDDEN");
    }

    @RepeatedTest(REPETITIONS)
    @DisplayName("[F-29][CM-07] 작성자가 댓글을 고치는 사이 다섯 번째 신고가 들어오면, 댓글은 검토 대기로 남는다")
    void commentReviseDuringFifthReportKeepsPendingReview() throws Exception {
        long commentId = saveComment(savePost());
        for (int i = 0; i < 4; i++) {
            reportService.reportComment(saveMember(), commentId, REPORT);
        }
        long fifthReporter = saveMember();
        pauseAfter(COMMENT_READ);

        Future<?> revise = executor.submit(() -> commentCommandService.revise(authorId, commentId, "고친 댓글"));
        awaitPaused();
        assertThat(runInMiddle(
                        () -> {
                            reportService.reportComment(fifthReporter, commentId, REPORT);
                            return null;
                        },
                        revise))
                .isNull();

        assertThat(commentStatusOf(commentId)).isEqualTo("PENDING_REVIEW");
    }

    @RepeatedTest(REPETITIONS)
    @DisplayName("[F-29][CM-06] 같은 글의 이미지를 5장씩 바꾸는 수정 두 건이 겹쳐도, 글에는 나중에 끝난 수정의 5장만 붙는다")
    void concurrentImageReplacesKeepPostCap() throws Exception {
        long postId = savePost();
        List<Long> first = uploadedImages(5);
        List<Long> second = uploadedImages(5);
        pauseAfter(CURRENT_IMAGES_READ);

        Future<?> firstRevise = executor.submit(() -> postCommandService.revise(authorId, postId, images(first)));
        awaitPaused();
        assertThat(runInMiddle(() -> postCommandService.revise(authorId, postId, images(second)), firstRevise))
                .isNull();

        assertThat(jdbc.queryForList(
                        "SELECT id FROM community_image WHERE post_id = ? ORDER BY display_order", Long.class, postId))
                .containsExactlyElementsOf(second);
    }

    private void pauseAfter(Set<String> calls) {
        pauseArmed.set(true);
        PauseHooks.afterCalls = calls;
        PauseHooks.action = this::pauseOnce;
    }

    private void pauseOnce() {
        if (!pauseArmed.compareAndSet(true, false)) {
            return;
        }
        paused.countDown();
        try {
            assertThat(resume.await(60, TimeUnit.SECONDS)).isTrue();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    private void awaitPaused() throws InterruptedException {
        assertThat(paused.await(10, TimeUnit.SECONDS)).isTrue();
    }

    private Throwable runInMiddle(Callable<?> middle, Future<?> pausedWork) throws Exception {
        Future<?> middleWork = executor.submit(middle);
        try {
            middleWork.get(MIDDLE_WAIT_SECONDS, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
        }
        resume.countDown();
        middleWork.get(60, TimeUnit.SECONDS);
        try {
            pausedWork.get(60, TimeUnit.SECONDS);
            return null;
        } catch (ExecutionException e) {
            return e.getCause();
        }
    }

    private long savePost() {
        return postCommandService.write(authorId, new CommunityPostWriteCommand("제목", "본문", null, List.of()));
    }

    private long saveComment(long postId) {
        return commentCommandService.write(authorId, postId, new CommunityCommentWriteCommand("댓글", null));
    }

    private long saveMember() {
        return memberRepository.saveAndFlush(aMember().build()).getId();
    }

    private List<Long> uploadedImages(int count) {
        List<Long> ids = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            long imageId = uploadService
                    .issue(authorId, new CommunityImageUploadCommand("image/jpeg", IMAGE_SIZE, null))
                    .imageId();
            storage.markUploaded(
                    jdbc.queryForObject("SELECT object_key FROM community_image WHERE id = ?", String.class, imageId),
                    IMAGE_SIZE);
            ids.add(imageId);
        }
        return ids;
    }

    private static CommunityPostReviseCommand retitle(String title) {
        return new CommunityPostReviseCommand(
                PatchField.of(title), PatchField.absent(), PatchField.absent(), PatchField.absent());
    }

    private static CommunityPostReviseCommand images(List<Long> imageIds) {
        return new CommunityPostReviseCommand(
                PatchField.absent(), PatchField.absent(), PatchField.absent(), PatchField.of(imageIds));
    }

    private String postStatusOf(long postId) {
        return jdbc.queryForObject("SELECT status FROM community_post WHERE id = ?", String.class, postId);
    }

    private String commentStatusOf(long commentId) {
        return jdbc.queryForObject("SELECT status FROM community_comment WHERE id = ?", String.class, commentId);
    }

    static final class PauseHooks {

        static volatile Set<String> afterCalls = Set.of();
        static volatile Runnable action;

        private PauseHooks() {}

        static void clear() {
            afterCalls = Set.of();
            action = null;
        }

        private static void after(String call) {
            Runnable hook = action;
            if (hook != null && afterCalls.contains(call)) {
                hook.run();
            }
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class PauseHookConfig {

        @Bean
        static BeanPostProcessor communityPauseHookPostProcessor() {
            return new BeanPostProcessor() {
                @Override
                public Object postProcessAfterInitialization(Object bean, String beanName) {
                    if (bean instanceof CommunityPostRepository) {
                        return wrap(bean, "CommunityPostRepository");
                    }
                    if (bean instanceof CommunityCommentRepository) {
                        return wrap(bean, "CommunityCommentRepository");
                    }
                    if (bean instanceof CommunityImageRepository) {
                        return wrap(bean, "CommunityImageRepository");
                    }
                    return bean;
                }
            };
        }

        private static Object wrap(Object target, String name) {
            return Proxy.newProxyInstance(
                    target.getClass().getClassLoader(), target.getClass().getInterfaces(), (proxy, method, args) -> {
                        Object result;
                        try {
                            result = method.invoke(target, args);
                        } catch (InvocationTargetException e) {
                            throw e.getCause();
                        }
                        PauseHooks.after(name + "." + method.getName());
                        return result;
                    });
        }
    }
}
