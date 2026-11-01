package com.moewadeul.idempotency.application;

import static com.moewadeul.common.testsupport.TestCsrf.csrf;
import static org.assertj.core.api.Assertions.assertThat;

import com.moewadeul.common.testsupport.IntegrationTest;
import com.moewadeul.common.testsupport.MemberFixture;
import com.moewadeul.common.testsupport.SpotFixture;
import com.moewadeul.common.testsupport.TestSequence;
import com.moewadeul.idempotency.domain.IdempotencyRecordRepository;
import com.moewadeul.member.application.verification.EmailVerificationIssuer;
import com.moewadeul.member.domain.Member;
import jakarta.servlet.http.Cookie;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

@IntegrationTest
@AutoConfigureMockMvc
class IdempotencyCompletionFailureIntegrationTest {

    private static final String PASSWORD = "Valid-pass1";
    private static final String KEY = "completion-failure-key";
    private static final String MEMORY_BODY =
            "{\"lat\":37.1,\"lng\":127.1,\"visitedDate\":\"2026-10-04\",\"nights\":1,\"content\":\"물가 옆 평지\"}";
    private static final AtomicBoolean FAIL_NEXT_COMPLETION_SAVE = new AtomicBoolean();

    @Autowired
    private MemberFixture memberFixture;

    @Autowired
    private SpotFixture spotFixture;

    @Autowired
    private MockMvcTester mvc;

    @Autowired
    private EmailVerificationIssuer emailVerificationIssuer;

    @Autowired
    private JdbcTemplate jdbc;

    @AfterEach
    void disarm() {
        FAIL_NEXT_COMPLETION_SAVE.set(false);
    }

    @Test
    @DisplayName("[NFR-03] 완료 기록이 실패하면 추억 기록도 남지 않고, 같은 키로 다시 보내면 한 번만 만들어진다")
    void memoryIsRolledBackWithFailedCompletion() {
        Cookie session = verifiedSession(memberFixture.saveWithPassword(PASSWORD));

        FAIL_NEXT_COMPLETION_SAVE.set(true);
        MvcTestResult first = post("/api/memories", session, MEMORY_BODY);

        assertThat(first.getResponse().getStatus()).isGreaterThanOrEqualTo(400);
        assertThat(count("memory")).isZero();
        assertThat(count("idempotency_record")).isZero();

        MvcTestResult retry = post("/api/memories", session, MEMORY_BODY);

        assertThat(retry.getResponse().getStatus()).isEqualTo(201);
        assertThat(count("memory")).isEqualTo(1);
        assertThat(count("idempotency_record")).isEqualTo(1);
    }

    @Test
    @DisplayName("[NFR-03] 사진 검사가 없는 요청도 완료 기록이 실패하면 일정이 남지 않고, 같은 키로 다시 보내면 한 번만 만들어진다")
    void soloTripIsRolledBackWithFailedCompletion() {
        Cookie session = verifiedSession(memberFixture.saveWithPassword(PASSWORD));
        String body = "{\"spotId\":%d,\"startDate\":\"2026-11-07\",\"endDate\":\"2026-11-09\"}"
                .formatted(spotFixture.insert());

        FAIL_NEXT_COMPLETION_SAVE.set(true);
        MvcTestResult first = post("/api/solo-trips", session, body);

        assertThat(first.getResponse().getStatus()).isGreaterThanOrEqualTo(400);
        assertThat(count("solo_trip")).isZero();
        assertThat(count("idempotency_record")).isZero();

        MvcTestResult retry = post("/api/solo-trips", session, body);

        assertThat(retry.getResponse().getStatus()).isEqualTo(201);
        assertThat(count("solo_trip")).isEqualTo(1);
        assertThat(count("idempotency_record")).isEqualTo(1);
    }

    private MvcTestResult post(String uri, Cookie session, String body) {
        return mvc.post()
                .uri(uri)
                .with(csrf())
                .cookie(session)
                .header("Idempotency-Key", KEY)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .exchange();
    }

    private int count(String table) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class);
    }

    private Cookie verifiedSession(Member member) {
        Cookie session = mvc.post()
                .uri("/api/auth/login")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(member.getEmail(), PASSWORD))
                .exchange()
                .getResponse()
                .getCookie("SESSION");
        String code = emailVerificationIssuer
                .issueFor(member.getId(), "127.0.0.1", TestSequence.next())
                .orElseThrow()
                .code();
        mvc.post()
                .uri("/api/me/email-verification")
                .with(csrf())
                .cookie(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"code\":\"%s\"}".formatted(code))
                .exchange();
        return session;
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class FailCompletionSaveOnce {

        @Bean
        static BeanPostProcessor failCompletionSaveOnce() {
            return new BeanPostProcessor() {
                @Override
                public Object postProcessAfterInitialization(Object bean, String beanName) {
                    if (!(bean instanceof IdempotencyRecordRepository)) {
                        return bean;
                    }
                    return Proxy.newProxyInstance(
                            bean.getClass().getClassLoader(),
                            bean.getClass().getInterfaces(),
                            (proxy, method, args) -> {
                                if (method.getName().equals("save")
                                        && FAIL_NEXT_COMPLETION_SAVE.compareAndSet(true, false)) {
                                    throw new DataAccessResourceFailureException("injected completion failure");
                                }
                                try {
                                    return method.invoke(bean, args);
                                } catch (InvocationTargetException e) {
                                    throw e.getCause();
                                }
                            });
                }
            };
        }
    }
}
