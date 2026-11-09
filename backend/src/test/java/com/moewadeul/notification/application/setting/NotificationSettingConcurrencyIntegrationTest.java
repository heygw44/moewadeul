package com.moewadeul.notification.application.setting;

import static com.moewadeul.member.domain.MemberBuilder.aMember;
import static org.assertj.core.api.Assertions.assertThat;

import com.moewadeul.common.testsupport.IntegrationTest;
import com.moewadeul.member.infra.MemberJpaRepository;
import com.moewadeul.notification.application.NotificationEventTypes;
import java.util.ArrayList;
import java.util.HashSet;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.RepeatedTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

@IntegrationTest
class NotificationSettingConcurrencyIntegrationTest {

    private static final int REPETITIONS = 5;
    private static final int THREADS = 5;

    @Autowired
    private NotificationSettingService notificationSettingService;

    @Autowired
    private MemberJpaRepository memberRepository;

    @Autowired
    private JdbcTemplate jdbc;

    private long memberId;

    @BeforeEach
    void setUp() {
        memberId = memberRepository.saveAndFlush(aMember().build()).getId();
    }

    @RepeatedTest(REPETITIONS)
    @DisplayName("[F-20][NFR-10] 설정이 없는 회원이 알림 설정 변경 5건을 동시에 보내면 모두 성공하고, 남은 설정은 요청 하나와 같다")
    void concurrentReplacesWithoutStoredSettingsAllSucceed() throws Exception {
        List<Throwable> failures = replaceConcurrently();

        assertThat(failures).isEmpty();
        assertThat(requests()).contains(storedDisabledTypes());
    }

    @RepeatedTest(REPETITIONS)
    @DisplayName("[F-20][NFR-10] 설정이 있는 회원이 알림 설정 변경 5건을 동시에 보내면 모두 성공하고, 남은 설정은 요청 하나와 같다")
    void concurrentReplacesWithStoredSettingsAllSucceed() throws Exception {
        notificationSettingService.replace(memberId, disabled(NotificationEventTypes.EMAIL_CONFIGURABLE));

        List<Throwable> failures = replaceConcurrently();

        assertThat(failures).isEmpty();
        assertThat(requests()).contains(storedDisabledTypes());
    }

    @RepeatedTest(REPETITIONS)
    @DisplayName("[F-20][NFR-10] 설정이 없는 이웃한 회원들이 알림 설정을 동시에 바꿔도 교착 없이 모두 성공한다")
    void concurrentReplacesOfNeighbouringMembersAllSucceed() throws Exception {
        List<Long> neighbours = new ArrayList<>();
        for (int i = 0; i < THREADS; i++) {
            neighbours.add(memberRepository.saveAndFlush(aMember().build()).getId());
        }
        List<NotificationSettingItem> items = disabled(NotificationEventTypes.EMAIL_CONFIGURABLE);

        List<Throwable> failures = runConcurrently(neighbours.stream()
                .map(neighbour -> (Callable<List<NotificationSettingItem>>)
                        () -> notificationSettingService.replace(neighbour, items))
                .toList());

        assertThat(failures).isEmpty();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM notification_setting", Integer.class))
                .isEqualTo(THREADS * items.size());
    }

    private static List<Set<String>> requests() {
        List<Set<String>> requests = new ArrayList<>();
        for (int i = 0; i < THREADS; i++) {
            requests.add(new HashSet<>(NotificationEventTypes.EMAIL_CONFIGURABLE.subList(0, i + 1)));
        }
        return requests;
    }

    private static List<NotificationSettingItem> disabled(List<String> types) {
        return types.stream()
                .map(type -> new NotificationSettingItem(type, false))
                .toList();
    }

    private Set<String> storedDisabledTypes() {
        return new HashSet<>(jdbc.queryForList(
                "SELECT type FROM notification_setting WHERE member_id = ? AND email_enabled = FALSE",
                String.class,
                memberId));
    }

    private List<Throwable> replaceConcurrently() throws Exception {
        return runConcurrently(requests().stream()
                .map(request -> disabled(NotificationEventTypes.EMAIL_CONFIGURABLE.stream()
                        .filter(request::contains)
                        .toList()))
                .map(items -> (Callable<List<NotificationSettingItem>>)
                        () -> notificationSettingService.replace(memberId, items))
                .toList());
    }

    private static List<Throwable> runConcurrently(List<Callable<List<NotificationSettingItem>>> actions)
            throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(actions.size());
        CountDownLatch ready = new CountDownLatch(actions.size());
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<?>> futures = new ArrayList<>();
            for (Callable<List<NotificationSettingItem>> action : actions) {
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

    private static List<Throwable> collectFailures(List<Future<?>> futures) throws InterruptedException {
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
}
