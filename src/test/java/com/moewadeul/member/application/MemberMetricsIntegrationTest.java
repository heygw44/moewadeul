package com.moewadeul.member.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.moewadeul.common.error.BusinessException;
import com.moewadeul.common.testsupport.IntegrationTest;
import com.moewadeul.common.testsupport.TestSequence;
import com.moewadeul.member.application.signup.MemberSignupService;
import com.moewadeul.member.application.signup.SignupCommand;
import com.moewadeul.member.application.signup.SignupResult;
import com.moewadeul.member.application.verification.EmailVerificationService;
import com.moewadeul.member.domain.MemberErrorCode;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

@IntegrationTest
class MemberMetricsIntegrationTest {

    private static final String VALID_PASSWORD = "Passw0rd!xyz";
    private static final String REQUEST_IP = "203.0.113.7";

    @Autowired
    private MemberSignupService memberSignupService;

    @Autowired
    private EmailVerificationService emailVerificationService;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private MeterRegistry meterRegistry;

    @Test
    @DisplayName("[NFR-09] 가입이 커밋되면 가입 수가 1 오른다")
    void countsCommittedSignup() {
        double before = signups();

        signUp(TestSequence.email());

        assertThat(signups()).isEqualTo(before + 1);
    }

    @Test
    @DisplayName("[NFR-09] 이미 쓰는 이메일로 가입에 실패하면 가입 수가 오르지 않는다")
    void doesNotCountRejectedSignup() {
        String email = TestSequence.email();
        signUp(email);
        double before = signups();

        assertThatThrownBy(() -> signUp(email))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", MemberErrorCode.MEMBER_EMAIL_DUPLICATED);

        assertThat(signups()).isEqualTo(before);
    }

    @Test
    @DisplayName("[NFR-09] 가입을 감싼 트랜잭션이 롤백되면 가입 수가 오르지 않는다")
    void doesNotCountRolledBackSignup() {
        double before = signups();

        transactionTemplate.executeWithoutResult(status -> {
            signUp(TestSequence.email());
            status.setRollbackOnly();
        });

        assertThat(signups()).isEqualTo(before);
    }

    @Test
    @DisplayName("[NFR-09] 맞는 코드로 이메일 인증을 마치면 인증 완료 수가 1 오르고, 틀린 코드는 세지 않는다")
    void countsOnlySuccessfulEmailVerification() {
        // given
        long memberId = signUp(TestSequence.email()).memberId();
        String code = emailVerificationService
                .issueFor(memberId, REQUEST_IP, TestSequence.next())
                .orElseThrow()
                .code();
        String wrongCode = code.equals("000000") ? "111111" : "000000";
        double before = emailVerifications();

        // when
        assertThatThrownBy(() -> emailVerificationService.verify(memberId, wrongCode))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", MemberErrorCode.EMAIL_CODE_INVALID);
        double afterWrongCode = emailVerifications();
        emailVerificationService.verify(memberId, code);

        // then
        assertThat(afterWrongCode).isEqualTo(before);
        assertThat(emailVerifications()).isEqualTo(before + 1);
    }

    private SignupResult signUp(String email) {
        return memberSignupService.signUp(
                new SignupCommand(email, VALID_PASSWORD, TestSequence.nickname(), REQUEST_IP));
    }

    private double signups() {
        return meterRegistry.get("moewadeul.member.signups").counter().count();
    }

    private double emailVerifications() {
        return meterRegistry
                .get("moewadeul.member.email.verifications")
                .counter()
                .count();
    }
}
