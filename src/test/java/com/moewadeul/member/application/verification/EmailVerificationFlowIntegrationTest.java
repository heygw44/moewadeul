package com.moewadeul.member.application.verification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.moewadeul.common.error.BusinessException;
import com.moewadeul.common.mail.MailMessage;
import com.moewadeul.common.mail.TestMailSender;
import com.moewadeul.common.outbox.OutboxMessage;
import com.moewadeul.common.testsupport.IntegrationTest;
import com.moewadeul.common.testsupport.MutableClock;
import com.moewadeul.common.testsupport.TestSequence;
import com.moewadeul.member.application.signup.MemberSignupService;
import com.moewadeul.member.application.signup.SignupCommand;
import com.moewadeul.member.application.signup.SignupResult;
import com.moewadeul.member.domain.MemberErrorCode;
import com.moewadeul.member.domain.MemberStatus;
import com.moewadeul.member.domain.verification.VerificationCode;
import com.moewadeul.outbox.application.OutboxProperties;
import com.moewadeul.outbox.application.OutboxPublisher;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

@IntegrationTest
class EmailVerificationFlowIntegrationTest {

    private static final String VALID_PASSWORD = "Passw0rd!xyz";
    private static final String REQUEST_IP = "203.0.113.7";
    private static final String MAIL_SUBJECT = "[뫼와들] 이메일 인증 코드";

    @Autowired
    private MemberSignupService memberSignupService;

    @Autowired
    private EmailVerificationService emailVerificationService;

    @Autowired
    private EmailVerificationRequestedHandler handler;

    @Autowired
    private OutboxPublisher publisher;

    @Autowired
    private OutboxProperties outboxProperties;

    @Autowired
    private TestMailSender mailSender;

    @Autowired
    private MutableClock clock;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void resetMailSender() {
        mailSender.reset();
    }

    @Test
    @DisplayName("[F-01][EV-01] 가입하면 UNVERIFIED 회원과 PENDING 이벤트가 생기고, 발행하면 메일 1통이 나가며 코드 원값은 DB에 없다")
    void signUpSendsCodeByMailAndStoresOnlyHash() {
        // given
        String email = TestSequence.email();

        // when
        SignupResult signedUp = signUp(email);

        // then
        Map<String, Object> event = findOnlyEvent();
        assertThat(event.get("event_type")).isEqualTo("EMAIL_VERIFICATION_REQUESTED");
        assertThat(event.get("aggregate_type")).isEqualTo("MEMBER");
        assertThat(event.get("aggregate_id")).isEqualTo(signedUp.memberId());
        assertThat(event.get("status")).isEqualTo("PENDING");
        assertThat(memberStatus(signedUp.memberId())).isEqualTo("UNVERIFIED");
        assertThat(countVerificationRows()).isZero();
        assertThat(mailSender.sent()).isEmpty();

        // when
        int processed = publisher.publishPending();

        // then
        assertThat(processed).isEqualTo(1);
        assertThat(findOnlyEvent().get("status")).isEqualTo("PUBLISHED");
        assertThat(mailSender.sent()).hasSize(1);
        MailMessage mail = mailSender.sent().get(0);
        assertThat(mail.to()).isEqualTo(email);
        assertThat(mail.subject()).isEqualTo(MAIL_SUBJECT);
        assertThat(mail.body()).contains("10분");
        String code = VerificationMails.extractCode(mail);
        assertThat(code).matches("\\d{6}");
        assertCodeIsStoredOnlyAsHash(signedUp.memberId(), code);
    }

    @Test
    @DisplayName("[F-01][EV-01] 메일로 받은 코드로 인증하면 회원이 ACTIVE가 된다")
    void codeFromMailVerifiesMember() {
        // given
        SignupResult signedUp = signUp(TestSequence.email());
        publisher.publishPending();
        String code = VerificationMails.extractCode(mailSender.sent().get(0));

        // when
        VerificationResult result = emailVerificationService.verify(signedUp.memberId(), code);

        // then
        assertThat(result.status()).isEqualTo(MemberStatus.ACTIVE);
        assertThat(memberStatus(signedUp.memberId())).isEqualTo("ACTIVE");
    }

    @Test
    @DisplayName("[F-01][EV-01] 가입 트랜잭션이 롤백되면 회원도 이벤트도 메일도 남지 않는다")
    void rolledBackSignupLeavesNoEventAndNoMail() {
        // when
        assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(status -> {
                    signUp(TestSequence.email());
                    throw new IllegalStateException("가입 이후 단계 실패");
                }))
                .isInstanceOf(IllegalStateException.class);
        int processed = publisher.publishPending();

        // then
        assertThat(countRows("member")).isZero();
        assertThat(countRows("outbox_event")).isZero();
        assertThat(processed).isZero();
        assertThat(mailSender.sent()).isEmpty();
    }

    @Test
    @DisplayName("[F-01][EV-06] 중복 이메일로 가입이 거부되면 두 번째 이벤트는 기록되지 않는다")
    void rejectedSignupRecordsNoEvent() {
        // given
        String email = TestSequence.email();
        signUp(email);

        // when
        assertThatThrownBy(() -> signUp(email))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(MemberErrorCode.MEMBER_EMAIL_DUPLICATED));
        publisher.publishPending();

        // then
        assertThat(countRows("outbox_event")).isEqualTo(1);
        assertThat(mailSender.sent()).hasSize(1);
    }

    @Test
    @DisplayName("[F-01][EV-01] 메일 발송이 실패하면 이벤트는 PENDING으로 남고, 재시도 간격이 지난 뒤 다시 발송하면 그 코드로 인증된다")
    void failedMailIsRetriedAfterRetryDelay() {
        // given
        SignupResult signedUp = signUp(TestSequence.email());
        mailSender.failNextSends(1);

        // when
        publisher.publishPending();

        // then
        Map<String, Object> afterFailure = findOnlyEvent();
        assertThat(afterFailure.get("status")).isEqualTo("PENDING");
        assertThat(afterFailure.get("attempt_count")).isEqualTo(1);
        assertThat(mailSender.sent()).isEmpty();

        // when
        assertThat(publisher.publishPending()).isZero();
        clock.advance(outboxProperties.retryDelay());
        publisher.publishPending();

        // then
        assertThat(findOnlyEvent().get("status")).isEqualTo("PUBLISHED");
        assertThat(mailSender.sent()).hasSize(1);
        String code = VerificationMails.extractCode(mailSender.sent().get(0));
        assertThat(emailVerificationService.verify(signedUp.memberId(), code).status())
                .isEqualTo(MemberStatus.ACTIVE);
    }

    @Test
    @DisplayName("[F-01][EV-03] 메일 서버 장애로 같은 발송 요청을 여섯 번 다시 시도해도 코드 행은 하나이고, 메일이 나간 뒤 회원은 60초 뒤 재발송할 수 있다")
    void retriedSendRequestKeepsOneCodeRowAndLeavesResendAvailable() {
        // given
        SignupResult signedUp = signUp(TestSequence.email());
        mailSender.failNextSends(6);
        for (int attempt = 0; attempt < 6; attempt++) {
            publisher.publishPending();
            clock.advance(outboxProperties.retryMaxDelay());
        }

        // when
        publisher.publishPending();

        // then
        assertThat(findOnlyEvent().get("status")).isEqualTo("PUBLISHED");
        assertThat(mailSender.sent()).hasSize(1);
        assertThat(countVerificationRows()).isEqualTo(1);

        // when & then
        clock.advance(Duration.ofSeconds(60));
        emailVerificationService.requestResend(signedUp.memberId(), REQUEST_IP);
        assertThat(countRows("outbox_event")).isEqualTo(2);
    }

    @Test
    @DisplayName("[F-01][EV-03] 같은 이벤트를 처리기가 두 번 처리하면 코드 행은 하나이고 두 메일의 코드가 같아서 먼저 받은 메일의 코드로 인증된다")
    void duplicateDeliveryResendsSameCode() {
        // given
        SignupResult signedUp = signUp(TestSequence.email());
        OutboxMessage message = toMessage(findOnlyEvent());

        // when
        handler.handle(message);
        clock.advance(Duration.ofSeconds(1));
        handler.handle(message);

        // then
        assertThat(countVerificationRows()).isEqualTo(1);
        List<MailMessage> mails = mailSender.sent();
        assertThat(mails).hasSize(2);
        String firstCode = VerificationMails.extractCode(mails.get(0));
        assertThat(VerificationMails.extractCode(mails.get(1))).isEqualTo(firstCode);
        assertThat(emailVerificationService
                        .verify(signedUp.memberId(), firstCode)
                        .status())
                .isEqualTo(MemberStatus.ACTIVE);
    }

    @Test
    @DisplayName("[F-01][EV-01] 이미 인증된 회원의 이벤트는 메일을 보내지 않고 코드도 만들지 않고 정상 종료한다")
    void alreadyVerifiedMemberGetsNoMail() {
        // given
        SignupResult signedUp = signUp(TestSequence.email());
        jdbcTemplate.update(
                "UPDATE member SET status = 'ACTIVE', email_verified_at = updated_at WHERE id = ?",
                signedUp.memberId());

        // when
        int processed = publisher.publishPending();

        // then
        assertThat(processed).isEqualTo(1);
        assertThat(findOnlyEvent().get("status")).isEqualTo("PUBLISHED");
        assertThat(mailSender.sent()).isEmpty();
        assertThat(countVerificationRows()).isZero();
    }

    @Test
    @DisplayName("[F-01] 읽을 수 없는 payload면 IllegalArgumentException이고 메시지에 payload를 싣지 않는다")
    void malformedPayloadIsRejectedWithoutEchoingIt() {
        // given
        String garbage = "garbage-203.0.113.99";
        OutboxMessage message = new OutboxMessage(
                1L, "EMAIL_VERIFICATION_REQUESTED", "MEMBER", 1L, garbage, MutableClock.DEFAULT_INSTANT);

        // when & then
        assertThatThrownBy(() -> handler.handle(message))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageNotContaining(garbage);
        assertThat(mailSender.sent()).isEmpty();
    }

    private SignupResult signUp(String email) {
        return memberSignupService.signUp(
                new SignupCommand(email, VALID_PASSWORD, TestSequence.nickname(), REQUEST_IP));
    }

    private void assertCodeIsStoredOnlyAsHash(long memberId, String code) {
        Map<String, Object> row = jdbcTemplate.queryForMap("SELECT code_hash, request_ip FROM email_verification");
        assertThat(row.get("code_hash")).isEqualTo(VerificationCode.hash(memberId, code));
        assertThat(row.get("request_ip")).isEqualTo(REQUEST_IP);
        assertThat(String.valueOf(row.get("code_hash"))).doesNotContain(code).hasSize(64);
        Map<String, Object> event = findOnlyEvent();
        assertThat(String.valueOf(event.get("payload"))).doesNotContain(code);
        assertThat(String.valueOf(event.get("last_error"))).doesNotContain(code);
    }

    private Map<String, Object> findOnlyEvent() {
        return jdbcTemplate.queryForMap("SELECT * FROM outbox_event");
    }

    private OutboxMessage toMessage(Map<String, Object> event) {
        return new OutboxMessage(
                ((Number) event.get("id")).longValue(),
                (String) event.get("event_type"),
                (String) event.get("aggregate_type"),
                ((Number) event.get("aggregate_id")).longValue(),
                (String) event.get("payload"),
                clock.instant());
    }

    private String memberStatus(long memberId) {
        return jdbcTemplate.queryForObject("SELECT status FROM member WHERE id = ?", String.class, memberId);
    }

    private int countVerificationRows() {
        return countRows("email_verification");
    }

    private int countRows(String table) {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class);
    }
}
