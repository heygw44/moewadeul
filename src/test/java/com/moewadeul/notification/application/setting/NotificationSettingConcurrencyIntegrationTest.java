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

    // 요청 i는 앞에서부터 i+1개 종류의 이메일을 끈다. 그래서 요청마다 남는 행의 집합이 다르다.
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

    // 같은 회원의 변경 요청을 스레드마다 하나씩 동시에 출발시키고, 던져진 예외를 모아 돌려준다.
    private List<Throwable> replaceConcurrently() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(THREADS);
        CountDownLatch ready = new CountDownLatch(THREADS);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<List<NotificationSettingItem>>> futures = new ArrayList<>();
            for (Set<String> request : requests()) {
                List<NotificationSettingItem> items = disabled(NotificationEventTypes.EMAIL_CONFIGURABLE.stream()
                        .filter(request::contains)
                        .toList());
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    start.await();
                    return notificationSettingService.replace(memberId, items);
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
