package com.moewadeul.memory.application;

import static com.moewadeul.member.domain.MemberBuilder.aMember;
import static org.assertj.core.api.Assertions.assertThat;

import com.moewadeul.common.image.application.ImageUploadCommand;
import com.moewadeul.common.moderation.ContentReportReason;
import com.moewadeul.common.patch.PatchField;
import com.moewadeul.common.testsupport.IntegrationTest;
import com.moewadeul.common.testsupport.TestImageStorage;
import com.moewadeul.member.infra.MemberJpaRepository;
import com.moewadeul.memory.application.image.MemoryImageUploadService;
import com.moewadeul.memory.application.moderation.MemoryModerationService;
import com.moewadeul.memory.application.report.MemoryReportCommand;
import com.moewadeul.memory.application.report.MemoryReportService;
import com.moewadeul.memory.domain.MemoryRepository;
import com.moewadeul.memory.domain.image.MemoryImageRepository;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.time.LocalDate;
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
class MemoryAuthorEditConcurrencyIntegrationTest {

    private static final int REPETITIONS = 3;
    private static final long MIDDLE_WAIT_SECONDS = 2;
    private static final long IMAGE_SIZE = 1024;
    private static final MemoryReportCommand REPORT = new MemoryReportCommand(ContentReportReason.SPAM, "광고 사진입니다");
    private static final Set<String> MEMORY_READ =
            Set.of("MemoryRepository.findById", "MemoryRepository.findByIdForUpdate");
    private static final Set<String> CURRENT_IMAGES_READ = Set.of("MemoryImageRepository.findIdsByParentId");

    @Autowired
    private MemoryCommandService commandService;

    @Autowired
    private MemoryModerationService moderationService;

    @Autowired
    private MemoryReportService reportService;

    @Autowired
    private MemoryImageUploadService uploadService;

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
    @DisplayName("[F-33][MM-11] 작성자가 기록을 고치는 사이 관리자가 기록을 숨기면, 기록은 HIDDEN으로 남는다")
    void reviseDuringHideKeepsHidden() throws Exception {
        long memoryId = savePublicMemory();
        long adminId = saveMember();
        pauseAfter(MEMORY_READ);

        Future<?> revise = executor.submit(() -> commandService.revise(authorId, memoryId, nights(3)));
        awaitPaused();
        assertThat(runInMiddle(() -> moderationService.hide(memoryId, adminId), revise))
                .isNull();

        assertThat(statusOf(memoryId)).isEqualTo("HIDDEN");
        assertThat(jdbc.queryForObject("SELECT nights FROM memory WHERE id = ?", Integer.class, memoryId))
                .isEqualTo(3);
    }

    @RepeatedTest(REPETITIONS)
    @DisplayName("[F-33][MM-10] 작성자가 기록을 고치는 사이 다섯 번째 신고가 들어오면, 기록은 검토 대기로 남는다")
    void reviseDuringFifthReportKeepsPendingReview() throws Exception {
        long memoryId = savePublicMemory();
        for (int i = 0; i < 4; i++) {
            reportService.report(saveMember(), memoryId, REPORT);
        }
        long fifthReporter = saveMember();
        pauseAfter(MEMORY_READ);

        Future<?> revise = executor.submit(() -> commandService.revise(authorId, memoryId, nights(3)));
        awaitPaused();
        assertThat(runInMiddle(
                        () -> {
                            reportService.report(fifthReporter, memoryId, REPORT);
                            return null;
                        },
                        revise))
                .isNull();

        assertThat(statusOf(memoryId)).isEqualTo("PENDING_REVIEW");
    }

    @RepeatedTest(REPETITIONS)
    @DisplayName("[F-33][MM-05] 같은 기록의 사진을 10장씩 바꾸는 수정 두 건이 겹쳐도, 기록에는 나중에 끝난 수정의 10장만 붙는다")
    void concurrentImageReplacesKeepMemoryCap() throws Exception {
        long memoryId = savePublicMemory();
        List<Long> first = uploadedImages(10);
        List<Long> second = uploadedImages(10);
        pauseAfter(CURRENT_IMAGES_READ);

        Future<?> firstRevise = executor.submit(() -> commandService.revise(authorId, memoryId, images(first)));
        awaitPaused();
        assertThat(runInMiddle(() -> commandService.revise(authorId, memoryId, images(second)), firstRevise))
                .isNull();

        assertThat(jdbc.queryForList(
                        "SELECT id FROM memory_image WHERE memory_id = ? ORDER BY display_order", Long.class, memoryId))
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

    private long savePublicMemory() {
        return commandService.write(
                authorId,
                new MemoryWriteCommand(null, 37.25, 127.25, LocalDate.of(2026, 10, 4), 1, null, "PUBLIC", List.of()));
    }

    private long saveMember() {
        return memberRepository.saveAndFlush(aMember().build()).getId();
    }

    private List<Long> uploadedImages(int count) {
        List<Long> ids = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            long imageId = uploadService
                    .issue(authorId, new ImageUploadCommand("image/jpeg", IMAGE_SIZE, null))
                    .imageId();
            storage.markUploaded(
                    jdbc.queryForObject("SELECT object_key FROM memory_image WHERE id = ?", String.class, imageId),
                    IMAGE_SIZE);
            ids.add(imageId);
        }
        return ids;
    }

    private static MemoryReviseCommand nights(int nights) {
        return revise(PatchField.of(nights), PatchField.absent());
    }

    private static MemoryReviseCommand images(List<Long> imageIds) {
        return revise(PatchField.absent(), PatchField.of(imageIds));
    }

    private static MemoryReviseCommand revise(PatchField<Integer> nights, PatchField<List<Long>> imageIds) {
        return new MemoryReviseCommand(
                PatchField.absent(),
                PatchField.absent(),
                PatchField.absent(),
                PatchField.absent(),
                nights,
                PatchField.absent(),
                PatchField.absent(),
                imageIds);
    }

    private String statusOf(long memoryId) {
        return jdbc.queryForObject("SELECT status FROM memory WHERE id = ?", String.class, memoryId);
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
        static BeanPostProcessor memoryPauseHookPostProcessor() {
            return new BeanPostProcessor() {
                @Override
                public Object postProcessAfterInitialization(Object bean, String beanName) {
                    if (bean instanceof MemoryRepository) {
                        return wrap(bean, "MemoryRepository");
                    }
                    if (bean instanceof MemoryImageRepository) {
                        return wrap(bean, "MemoryImageRepository");
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
