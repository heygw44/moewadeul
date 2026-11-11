package com.moewadeul.program.api.enrollment;

import static com.moewadeul.common.testsupport.TestCsrf.csrf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;

import com.jayway.jsonpath.JsonPath;
import com.moewadeul.common.security.LoginMember;
import com.moewadeul.common.testsupport.IntegrationTest;
import com.moewadeul.common.testsupport.MutableClock;
import com.moewadeul.idempotency.application.IdempotencyExecutor;
import com.moewadeul.idempotency.application.IdempotentRequest;
import com.moewadeul.idempotency.domain.IdempotencyRecord;
import com.moewadeul.idempotency.domain.IdempotencyRecordRepository;
import com.moewadeul.member.infra.MemberJpaRepository;
import com.moewadeul.program.application.ProgramApplyFixture;
import com.moewadeul.program.application.enrollment.ProgramApplyService;
import com.moewadeul.program.application.enrollment.ProgramPaymentService;
import com.zaxxer.hikari.HikariDataSource;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

@IntegrationTest
@AutoConfigureMockMvc
class ProgramSoldOutMarkIntegrationTest {

    private static final Duration MARK_TTL = Duration.ofSeconds(1);
    private static final long POOL_DEADLINE_SECONDS = 5;

    @Autowired
    private MockMvcTester mvc;

    @Autowired
    private ProgramApplyService applyService;

    @Autowired
    private ProgramPaymentService paymentService;

    @Autowired
    private IdempotencyExecutor idempotencyExecutor;

    @Autowired
    private IdempotencyRecordRepository recordRepository;

    @Autowired
    private MemberJpaRepository memberRepository;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private DataSource dataSource;

    @Autowired
    private MutableClock clock;

    private ProgramApplyFixture fixture;

    @BeforeEach
    void setUp() {
        fixture = new ProgramApplyFixture(jdbc, memberRepository);
    }

    @Test
    @DisplayName(
            "[F-18][NFR-02] 매진을 확인한 행사의 신청 처리는 커넥션 풀이 모두 쓰이는 중에도 풀을 기다리지 않고 409 PROGRAM_SOLD_OUT이다. 로그인 세션 읽기는 이 검사 밖이다")
    void markedSoldOutRejectsWithoutConnection() throws Exception {
        // given
        SoldOut soldOut = soldOutProgram(false);
        long withSeats = fixture.saveProgram(5, false);
        long outsider = fixture.saveMember();
        HikariDataSource pool = dataSource.unwrap(HikariDataSource.class);
        assertThat(pool.getConnectionTimeout()).isGreaterThan(TimeUnit.SECONDS.toMillis(POOL_DEADLINE_SECONDS));
        List<Connection> held = new ArrayList<>();
        ExecutorService client = Executors.newFixedThreadPool(2);
        try {
            for (int i = 0; i < pool.getMaximumPoolSize(); i++) {
                held.add(pool.getConnection());
            }

            // when
            Future<MvcTestResult> rejected = client.submit(() -> apply(outsider, soldOut.programId(), "pool-1"));
            Future<MvcTestResult> needsPool = client.submit(() -> apply(outsider, withSeats, "pool-2"));

            // then
            assertCode(rejected.get(POOL_DEADLINE_SECONDS, TimeUnit.SECONDS), HttpStatus.CONFLICT, "PROGRAM_SOLD_OUT");
            assertThatThrownBy(() -> needsPool.get(1, TimeUnit.SECONDS)).isInstanceOf(TimeoutException.class);
            releaseAll(held);
            assertThat(needsPool.get(POOL_DEADLINE_SECONDS, TimeUnit.SECONDS)).hasStatus(HttpStatus.CREATED);
        } finally {
            releaseAll(held);
            client.shutdownNow();
        }
    }

    @Test
    @DisplayName("[F-18][PG-03] 매진 표시 동안 취소로 빈 자리는 TTL이 지나야 신청할 수 있다")
    void freedSeatOpensAfterTtl() {
        // given
        SoldOut soldOut = soldOutProgram(false);
        long outsider = fixture.saveMember();
        paymentService.cancel(soldOut.holder(), soldOut.holderApplicationId());

        // when
        MvcTestResult withinTtl = apply(outsider, soldOut.programId(), "within-ttl");
        clock.advance(MARK_TTL);
        MvcTestResult afterTtl = apply(outsider, soldOut.programId(), "after-ttl");

        // then
        assertCode(withinTtl, HttpStatus.CONFLICT, "PROGRAM_SOLD_OUT");
        assertThat(afterTtl).hasStatus(HttpStatus.CREATED);
        assertThat(fixture.activeCount(soldOut.programId())).isEqualTo(1);
    }

    @Test
    @DisplayName("[F-18][NFR-03] 매진 표시가 있어도 자리를 쥔 회원이 같은 키로 다시 보내면 처음 결과를 받고, 새 키면 PROGRAM_ALREADY_APPLIED이다")
    void seatHolderKeepsIdempotentReplay() {
        // given
        SoldOut soldOut = soldOutProgram(false);

        // when
        MvcTestResult replay = apply(soldOut.holder(), soldOut.programId(), soldOut.holderKey());
        MvcTestResult newKey = apply(soldOut.holder(), soldOut.programId(), "holder-new-key");

        // then
        assertThat(replay).hasStatus(HttpStatus.CREATED);
        assertThat(applicationId(replay)).isEqualTo(soldOut.holderApplicationId());
        assertCode(newKey, HttpStatus.CONFLICT, "PROGRAM_ALREADY_APPLIED");
    }

    @Test
    @DisplayName("[F-18][NFR-03] 매진 표시 뒤 빈 자리를 얻은 회원도 같은 키로 다시 보내면 처음 결과를 받는다")
    void seatTakenAfterMarkKeepsIdempotentReplay() {
        // given
        SoldOut soldOut = soldOutProgram(false);
        paymentService.cancel(soldOut.holder(), soldOut.holderApplicationId());
        long late = fixture.saveMember();
        long lateApplicationId = applyPastMark(late, soldOut.programId(), "late-key");

        // when
        MvcTestResult replay = apply(late, soldOut.programId(), "late-key");
        MvcTestResult outsider = apply(fixture.saveMember(), soldOut.programId(), "outsider-key");

        // then
        assertThat(replay).hasStatus(HttpStatus.CREATED);
        assertThat(applicationId(replay)).isEqualTo(lateApplicationId);
        assertCode(outsider, HttpStatus.CONFLICT, "PROGRAM_SOLD_OUT");
    }

    @Test
    @DisplayName("[F-18][PG-02] 매진 표시 동안 숙박 행사에 신뢰 단계가 모자란 회원은 403 대신 409 PROGRAM_SOLD_OUT을 받는다")
    void markComesBeforeTrustLevel() {
        // given
        SoldOut soldOut = soldOutProgram(true);
        long levelZero = fixture.saveMember();

        // when
        MvcTestResult result = apply(levelZero, soldOut.programId(), "level-zero");

        // then
        assertCode(result, HttpStatus.CONFLICT, "PROGRAM_SOLD_OUT");
    }

    @Test
    @DisplayName("[F-18][NFR-03] 매진 표시 동안 취소한 신청의 키를 다시 보내면 처음 201 대신 409 PROGRAM_SOLD_OUT이다")
    void markComesBeforeReplayOfCanceledApplication() {
        // given
        long programId = fixture.saveProgram(1, false);
        long member = fixture.saveMember();
        MvcTestResult first = apply(member, programId, "canceled-key");
        paymentService.cancel(member, applicationId(first));
        apply(fixture.saveMember(), programId, "fill");
        apply(fixture.saveMember(), programId, "probe");

        // when
        MvcTestResult replay = apply(member, programId, "canceled-key");

        // then
        assertThat(first).hasStatus(HttpStatus.CREATED);
        assertCode(replay, HttpStatus.CONFLICT, "PROGRAM_SOLD_OUT");
    }

    @Test
    @DisplayName(
            "[F-18][NFR-03] 매진 표시 동안 처리 중인 키와 다른 행사에 쓴 키는 409 IDEMPOTENCY_IN_PROGRESS와 422 대신 409 PROGRAM_SOLD_OUT이다")
    void markComesBeforeClaimChecks() throws Exception {
        // given
        SoldOut soldOut = soldOutProgram(false);
        long outsider = fixture.saveMember();
        recordRepository.saveAndFlush(
                IdempotencyRecord.claim(outsider, "busy-key", requestHash(soldOut.programId()), clock.instant()));
        long otherProgram = fixture.saveProgram(5, false);
        assertThat(apply(outsider, otherProgram, "used-key")).hasStatus(HttpStatus.CREATED);

        // when
        MvcTestResult inProgress = apply(outsider, soldOut.programId(), "busy-key");
        MvcTestResult reused = apply(outsider, soldOut.programId(), "used-key");

        // then
        assertCode(inProgress, HttpStatus.CONFLICT, "PROGRAM_SOLD_OUT");
        assertCode(reused, HttpStatus.CONFLICT, "PROGRAM_SOLD_OUT");
    }

    @Test
    @DisplayName("[F-18] 신청 마감이 매진 표시보다 먼저 오면 마감 뒤 신청은 400 PROGRAM_NOT_IN_APPLY_PERIOD이다")
    void applyCloseEndsMark() {
        // given
        long programId = fixture.saveProgram(
                1,
                false,
                clock.instant().minus(Duration.ofHours(1)),
                clock.instant().plusMillis(500),
                "SCHEDULED");
        apply(fixture.saveMember(), programId, "fill");
        assertCode(apply(fixture.saveMember(), programId, "probe"), HttpStatus.CONFLICT, "PROGRAM_SOLD_OUT");
        clock.advance(Duration.ofMillis(500));

        // when
        MvcTestResult result = apply(fixture.saveMember(), programId, "after-close");

        // then
        assertCode(result, HttpStatus.BAD_REQUEST, "PROGRAM_NOT_IN_APPLY_PERIOD");
    }

    @RepeatedTest(3)
    @DisplayName("[F-18][NFR-02] 매진 표시가 있어도 동시 신청의 자리 수는 정원을 넘지 않고, TTL 뒤 빈 자리는 한 명만 얻는다")
    void concurrentAppliesNeverExceedCapacity() throws Exception {
        // given
        int capacity = 5;
        long programId = fixture.saveProgram(capacity, false);

        // when
        List<MvcTestResult> opening = applyConcurrently(programId, 30, "open");
        assertCode(apply(fixture.saveMember(), programId, "probe"), HttpStatus.CONFLICT, "PROGRAM_SOLD_OUT");
        Map<String, Object> firstHolder = jdbc.queryForMap(
                "SELECT id, member_id FROM program_application WHERE program_id = ? ORDER BY id LIMIT 1", programId);
        paymentService.cancel(
                ((Number) firstHolder.get("member_id")).longValue(), ((Number) firstHolder.get("id")).longValue());
        List<MvcTestResult> withinTtl = applyConcurrently(programId, 30, "within");
        int activeWithinTtl = fixture.activeCount(programId);
        clock.advance(MARK_TTL);
        List<MvcTestResult> afterTtl = applyConcurrently(programId, 10, "after");

        // then
        assertThat(statuses(opening, HttpStatus.CREATED)).isEqualTo(capacity);
        assertThat(statuses(opening, HttpStatus.CONFLICT)).isEqualTo(30 - capacity);
        assertThat(statuses(withinTtl, HttpStatus.CONFLICT)).isEqualTo(30);
        assertThat(activeWithinTtl).isEqualTo(capacity - 1);
        assertThat(statuses(afterTtl, HttpStatus.CREATED)).isEqualTo(1);
        assertThat(statuses(afterTtl, HttpStatus.CONFLICT)).isEqualTo(9);
        assertThat(fixture.activeCount(programId)).isEqualTo(capacity);
    }

    private record SoldOut(long programId, long holder, String holderKey, long holderApplicationId) {}

    private SoldOut soldOutProgram(boolean overnight) {
        long programId = fixture.saveProgram(1, overnight);
        long holder = fixture.saveVerifiedMember();
        MvcTestResult held = apply(holder, programId, "holder-key");
        assertThat(held).hasStatus(HttpStatus.CREATED);
        assertCode(apply(fixture.saveVerifiedMember(), programId, "probe"), HttpStatus.CONFLICT, "PROGRAM_SOLD_OUT");
        return new SoldOut(programId, holder, "holder-key", applicationId(held));
    }

    private long applyPastMark(long memberId, long programId, String key) {
        return idempotencyExecutor
                .execute(
                        new IdempotentRequest(memberId, key, operation(programId), null),
                        HttpStatus.CREATED,
                        ProgramApplyResponse.class,
                        () -> applyService.rejectIfSoldOutWithoutLock(memberId, programId),
                        () -> ProgramApplyResponse.from(applyService.apply(memberId, programId)))
                .applicationId();
    }

    private List<MvcTestResult> applyConcurrently(long programId, int applicants, String keyPrefix) throws Exception {
        List<Long> members = new ArrayList<>();
        for (int i = 0; i < applicants; i++) {
            members.add(fixture.saveMember());
        }
        ExecutorService executor = Executors.newFixedThreadPool(applicants);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<MvcTestResult>> futures = new ArrayList<>();
            for (long member : members) {
                futures.add(executor.submit(() -> {
                    start.await();
                    return apply(member, programId, keyPrefix + "-" + member);
                }));
            }
            start.countDown();
            List<MvcTestResult> results = new ArrayList<>();
            for (Future<MvcTestResult> future : futures) {
                results.add(future.get(60, TimeUnit.SECONDS));
            }
            return results;
        } finally {
            executor.shutdownNow();
        }
    }

    private MvcTestResult apply(long memberId, long programId, String key) {
        LoginMember loginMember = new LoginMember(memberId, "USER", true);
        return mvc.post()
                .uri("/api/programs/" + programId + "/applications")
                .header("Idempotency-Key", key)
                .with(csrf())
                .with(authentication(UsernamePasswordAuthenticationToken.authenticated(
                        loginMember,
                        null,
                        List.of(
                                new SimpleGrantedAuthority("ROLE_USER"),
                                new SimpleGrantedAuthority(LoginMember.AUTHORITY_EMAIL_VERIFIED)))))
                .exchange();
    }

    private static String operation(long programId) {
        return "POST /api/programs/" + programId + "/applications";
    }

    private static String requestHash(long programId) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256")
                .digest((operation(programId) + "\n").getBytes(StandardCharsets.UTF_8));
        return HexFormat.of().formatHex(digest);
    }

    private static long applicationId(MvcTestResult result) {
        Number id = JsonPath.read(
                new String(result.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8), "$.applicationId");
        return id.longValue();
    }

    private static long statuses(List<MvcTestResult> results, HttpStatus status) {
        return results.stream()
                .filter(result -> result.getResponse().getStatus() == status.value())
                .count();
    }

    private static void assertCode(MvcTestResult result, HttpStatus status, String code) {
        assertThat(result).hasStatus(status);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo(code);
    }

    private static void releaseAll(List<Connection> held) {
        for (Connection connection : held) {
            try {
                connection.close();
            } catch (SQLException ignored) {
            }
        }
        held.clear();
    }
}
