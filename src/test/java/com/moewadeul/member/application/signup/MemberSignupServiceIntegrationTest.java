package com.moewadeul.member.application.signup;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.moewadeul.common.error.BusinessException;
import com.moewadeul.common.error.CommonErrorCode;
import com.moewadeul.common.error.RateLimitedException;
import com.moewadeul.common.testsupport.IntegrationTest;
import com.moewadeul.common.testsupport.MutableClock;
import com.moewadeul.common.testsupport.TestSequence;
import com.moewadeul.member.domain.Member;
import com.moewadeul.member.domain.MemberBuilder;
import com.moewadeul.member.domain.MemberErrorCode;
import com.moewadeul.member.domain.MemberException;
import com.moewadeul.member.domain.MemberRole;
import com.moewadeul.member.domain.MemberStatus;
import com.moewadeul.member.domain.signup.DisposableEmailDomain;
import com.moewadeul.member.domain.signup.DisposableEmailDomainSource;
import com.moewadeul.member.infra.MemberJpaRepository;
import com.moewadeul.member.infra.signup.DisposableEmailDomainJpaRepository;
import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

@IntegrationTest
class MemberSignupServiceIntegrationTest {

    private static final String VALID_PASSWORD = "Passw0rd!xyz";
    private static final String REQUEST_IP = "203.0.113.7";

    @Autowired
    MemberSignupService memberSignupService;

    @Autowired
    MemberJpaRepository memberJpaRepository;

    @Autowired
    DisposableEmailDomainJpaRepository disposableEmailDomainJpaRepository;

    @Autowired
    PasswordEncoder passwordEncoder;

    @Autowired
    MutableClock clock;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    @DisplayName("[F-01] 유효한 입력이면 UNVERIFIED USER 회원이 시계 시각으로 저장된다")
    void signUpStoresUnverifiedUser() {
        // given
        String email = TestSequence.email();

        // when
        SignupResult result =
                memberSignupService.signUp(new SignupCommand(email, VALID_PASSWORD, nickname(), REQUEST_IP));

        // then
        assertThat(result.memberId()).isNotNull();
        assertThat(result.status()).isEqualTo(MemberStatus.UNVERIFIED);
        Member member = memberJpaRepository.findById(result.memberId()).orElseThrow();
        assertThat(member.getStatus()).isEqualTo(MemberStatus.UNVERIFIED);
        assertThat(member.getRole()).isEqualTo(MemberRole.USER);
        assertThat(member.getEmail()).isEqualTo(email);
        assertThat(member.getCreatedAt()).isEqualTo(MutableClock.DEFAULT_INSTANT);
    }

    @Test
    @DisplayName("[F-01][EV-03] 같은 IP의 인증 메일 발송 요청이 한 시간에 20건이면 가입은 SIGNUP_LIMITED이고 회원도 발송 요청도 만들지 않는다")
    void signupIsRejectedWhenIpReachedHourlyLimit() {
        // given
        for (int signedUp = 0; signedUp < 20; signedUp++) {
            memberSignupService.signUp(new SignupCommand(TestSequence.email(), VALID_PASSWORD, nickname(), REQUEST_IP));
        }

        // when & then
        assertThatThrownBy(() -> memberSignupService.signUp(
                        new SignupCommand(TestSequence.email(), VALID_PASSWORD, nickname(), REQUEST_IP)))
                .isInstanceOfSatisfying(RateLimitedException.class, e -> {
                    assertThat(e.getErrorCode()).isEqualTo(MemberErrorCode.SIGNUP_LIMITED);
                    assertThat(e.retryAfter()).isEqualTo(Duration.ofHours(1));
                });
        assertThat(countRows("member")).isEqualTo(20);
        assertThat(countRows("outbox_event")).isEqualTo(20);

        // when & then
        memberSignupService.signUp(new SignupCommand(TestSequence.email(), VALID_PASSWORD, nickname(), "203.0.113.8"));
        clock.advance(Duration.ofHours(1));
        memberSignupService.signUp(new SignupCommand(TestSequence.email(), VALID_PASSWORD, nickname(), REQUEST_IP));
        assertThat(countRows("member")).isEqualTo(22);
    }

    @Test
    @DisplayName("[F-01][EV-03] 같은 IP의 가입 요청이 19건이면 20번째 가입은 받는다")
    void signupIsAcceptedWhenIpIsOneBelowHourlyLimit() {
        // given
        for (int signedUp = 0; signedUp < 19; signedUp++) {
            memberSignupService.signUp(new SignupCommand(TestSequence.email(), VALID_PASSWORD, nickname(), REQUEST_IP));
        }

        // when
        SignupResult result = memberSignupService.signUp(
                new SignupCommand(TestSequence.email(), VALID_PASSWORD, nickname(), REQUEST_IP));

        // then
        assertThat(result.memberId()).isNotNull();
        assertThat(countRows("member")).isEqualTo(20);
    }

    @Test
    @DisplayName("[F-01][EV-03] 같은 IP에서 이미 쓰인 이메일로 가입을 20번 시도하면 그다음 가입은 중복이든 새 이메일이든 SIGNUP_LIMITED다")
    void duplicateEmailAttemptsCountTowardIpLimit() {
        // given
        String taken = TestSequence.email();
        memberJpaRepository.saveAndFlush(MemberBuilder.aMember().email(taken).build());
        for (int attempted = 0; attempted < 20; attempted++) {
            SignupCommand duplicate = new SignupCommand(taken, VALID_PASSWORD, nickname(), REQUEST_IP);
            assertThatThrownBy(() -> memberSignupService.signUp(duplicate))
                    .isInstanceOfSatisfying(
                            MemberException.class,
                            e -> assertThat(e.getErrorCode()).isEqualTo(MemberErrorCode.MEMBER_EMAIL_DUPLICATED));
        }

        // when & then
        assertSignupLimited(new SignupCommand(taken, VALID_PASSWORD, nickname(), REQUEST_IP));
        assertSignupLimited(new SignupCommand(TestSequence.email(), VALID_PASSWORD, nickname(), REQUEST_IP));
        assertThat(countRows("member")).isEqualTo(1);

        // when & then
        clock.advance(Duration.ofHours(1));
        memberSignupService.signUp(new SignupCommand(TestSequence.email(), VALID_PASSWORD, nickname(), REQUEST_IP));
        assertThat(countRows("member")).isEqualTo(2);
    }

    @Test
    @DisplayName("[F-01][EV-03] 같은 IP에서 이미 쓰인 닉네임으로 가입을 20번 시도해도 그다음 가입은 SIGNUP_LIMITED다")
    void duplicateNicknameAttemptsCountTowardIpLimit() {
        // given
        String taken = nickname();
        memberJpaRepository.saveAndFlush(MemberBuilder.aMember().nickname(taken).build());
        for (int attempted = 0; attempted < 20; attempted++) {
            SignupCommand duplicate = new SignupCommand(TestSequence.email(), VALID_PASSWORD, taken, REQUEST_IP);
            assertThatThrownBy(() -> memberSignupService.signUp(duplicate))
                    .isInstanceOfSatisfying(
                            MemberException.class,
                            e -> assertThat(e.getErrorCode()).isEqualTo(MemberErrorCode.MEMBER_NICKNAME_DUPLICATED));
        }

        // when & then
        assertSignupLimited(new SignupCommand(TestSequence.email(), VALID_PASSWORD, nickname(), REQUEST_IP));
        assertThat(countRows("member")).isEqualTo(1);
    }

    @Test
    @DisplayName("[F-01][EV-06] ASCII가 아닌 글자가 든 이메일은 가입 서비스에서 거부하고 회원을 만들지 않는다")
    void nonAsciiEmailIsRejected() {
        // when & then
        assertThatThrownBy(() -> memberSignupService.signUp(
                        new SignupCommand("ｖictim@example.com", VALID_PASSWORD, nickname(), REQUEST_IP)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(countRows("member")).isZero();
    }

    @Test
    @DisplayName("[F-01][PW-01] 비밀번호는 BCrypt 해시로 저장된다")
    void passwordIsStoredAsBcryptHash() {
        // when
        SignupResult result = memberSignupService.signUp(
                new SignupCommand(TestSequence.email(), VALID_PASSWORD, nickname(), REQUEST_IP));

        // then
        String hash =
                memberJpaRepository.findById(result.memberId()).orElseThrow().getPasswordHash();
        assertThat(hash).isNotEqualTo(VALID_PASSWORD);
        assertThat(hash).startsWith("$2");
        assertThat(hash.length()).isLessThanOrEqualTo(100);
        assertThat(passwordEncoder.matches(VALID_PASSWORD, hash)).isTrue();
    }

    @Test
    @DisplayName("[F-01][EV-06] 이메일은 소문자로 저장되고 대소문자만 다른 이메일은 중복이다")
    void emailIsLowercasedAndDuplicateIgnoresCase() {
        // given
        SignupResult first = memberSignupService.signUp(
                new SignupCommand("User@Example.com", VALID_PASSWORD, nickname(), REQUEST_IP));

        // when & then
        assertThat(memberJpaRepository.findById(first.memberId()).orElseThrow().getEmail())
                .isEqualTo("user@example.com");
        SignupCommand duplicate = new SignupCommand("USER@EXAMPLE.COM", VALID_PASSWORD, nickname(), REQUEST_IP);
        assertThatThrownBy(() -> memberSignupService.signUp(duplicate))
                .isInstanceOfSatisfying(
                        MemberException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(MemberErrorCode.MEMBER_EMAIL_DUPLICATED));
        assertThat(memberJpaRepository.count()).isEqualTo(1);
    }

    @ParameterizedTest
    @ValueSource(strings = {" ", "\u00A0", "\u3164", "\u200B"})
    @DisplayName("[F-01] 있는 닉네임 뒤에 공백이나 보이지 않는 문자를 붙여 같아 보이게 해도 INVALID_INPUT이고 회원이 만들어지지 않는다")
    void lookAlikeNicknameIsRejected(String invisibleSuffix) {
        // given
        String nickname = nickname();
        memberSignupService.signUp(new SignupCommand(TestSequence.email(), VALID_PASSWORD, nickname, REQUEST_IP));
        SignupCommand lookAlike =
                new SignupCommand(TestSequence.email(), VALID_PASSWORD, nickname + invisibleSuffix, REQUEST_IP);

        // when & then
        assertThatThrownBy(() -> memberSignupService.signUp(lookAlike))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(CommonErrorCode.INVALID_INPUT));
        assertThat(memberJpaRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("[F-01] 닉네임이 중복이면 MEMBER_NICKNAME_DUPLICATED")
    void duplicateNicknameIsRejected() {
        // given
        String nickname = nickname();
        memberSignupService.signUp(new SignupCommand(TestSequence.email(), VALID_PASSWORD, nickname, REQUEST_IP));

        // when & then
        SignupCommand duplicate = new SignupCommand(TestSequence.email(), VALID_PASSWORD, nickname, REQUEST_IP);
        assertThatThrownBy(() -> memberSignupService.signUp(duplicate))
                .isInstanceOfSatisfying(
                        MemberException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(MemberErrorCode.MEMBER_NICKNAME_DUPLICATED));
        assertThat(memberJpaRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("[F-01][EV-04] 차단 목록 도메인과 그 하위 도메인 이메일은 거부되고, 목록에 없는 도메인은 통과한다")
    void disposableEmailDomainIsRejected() {
        // given
        disposableEmailDomainJpaRepository.save(DisposableEmailDomain.of(
                "mailinator.com", DisposableEmailDomainSource.PUBLIC, MutableClock.DEFAULT_INSTANT));

        // when & then
        assertDisposableRejected("x@mailinator.com");
        assertDisposableRejected("x@a.mailinator.com");
        assertThat(memberJpaRepository.count()).isZero();
        SignupResult result = memberSignupService.signUp(
                new SignupCommand("x@notmailinator.com", VALID_PASSWORD, nickname(), REQUEST_IP));
        assertThat(result.memberId()).isNotNull();
    }

    @Test
    @DisplayName("[F-01][PW-01] 비밀번호 정책을 어기면 MEMBER_PASSWORD_POLICY이고 회원이 만들어지지 않는다")
    void passwordPolicyViolationIsRejected() {
        // given
        SignupCommand command = new SignupCommand(TestSequence.email(), "Passw0r!x", nickname(), REQUEST_IP);

        // when & then
        assertThatThrownBy(() -> memberSignupService.signUp(command))
                .isInstanceOfSatisfying(
                        MemberException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(MemberErrorCode.MEMBER_PASSWORD_POLICY));
        assertThat(memberJpaRepository.count()).isZero();
    }

    private void assertDisposableRejected(String email) {
        SignupCommand command = new SignupCommand(email, VALID_PASSWORD, nickname(), REQUEST_IP);
        assertThatThrownBy(() -> memberSignupService.signUp(command))
                .isInstanceOfSatisfying(
                        MemberException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(MemberErrorCode.MEMBER_DISPOSABLE_EMAIL));
    }

    private void assertSignupLimited(SignupCommand command) {
        assertThatThrownBy(() -> memberSignupService.signUp(command))
                .isInstanceOfSatisfying(
                        RateLimitedException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(MemberErrorCode.SIGNUP_LIMITED));
    }

    private int countRows(String table) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class);
    }

    private String nickname() {
        return TestSequence.nickname();
    }
}
