package com.moewadeul.member.application.password;

import com.moewadeul.common.error.RateLimitedException;
import com.moewadeul.member.application.LoginAttemptService;
import com.moewadeul.member.application.MemberSessions;
import com.moewadeul.member.domain.Member;
import com.moewadeul.member.domain.MemberErrorCode;
import com.moewadeul.member.domain.MemberException;
import com.moewadeul.member.domain.MemberRepository;
import com.moewadeul.member.domain.MemberStatus;
import com.moewadeul.member.domain.Password;
import java.time.Duration;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * 로그인한 회원이 현재 비밀번호를 확인한 뒤 비밀번호를 바꾼다. 비밀번호는 로그와 예외 메시지에 싣지 않는다.
 *
 * <p>이 클래스에는 {@code @Transactional}을 두지 않는다. 현재 비밀번호가 틀리면 실패 기록을 남기고 예외를 던지는데,
 * 트랜잭션 안에서 던지면 실패 기록도 롤백되어 잠금이 걸리지 않는다. 또 Spring Session JDBC는 세션 작업마다
 * 새 트랜잭션(두 번째 커넥션)을 열어서, 트랜잭션 안에서 세션을 지우면 동시 요청이 커넥션 풀 크기에 닿을 때 서로 기다리며 멈춘다.
 * 그래서 비밀번호 변경은 {@link PasswordChangeApplier}의 트랜잭션이 맡고, 세션 삭제는 그 트랜잭션이 커밋된 뒤에 한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PasswordChangeService {

    private final MemberRepository memberRepository;
    private final LoginAttemptService loginAttemptService;
    private final PasswordEncoder passwordEncoder;
    private final PasswordChangeApplier passwordChangeApplier;
    private final MemberSessions memberSessions;

    /**
     * 호출하면 비밀번호를 바꾸고, 요청한 세션을 뺀 그 회원의 세션을 모두 지운다.
     * 커밋 뒤 세션 삭제가 실패하면 비밀번호는 이미 바뀐 상태이고 예전 세션이 남을 수 있다.
     *
     * @throws RateLimitedException 로그인 실패가 쌓여 잠겨 있으면 LOGIN_LOCKED
     * @throws MemberException 현재 비밀번호가 틀리면 MEMBER_CURRENT_PASSWORD_MISMATCH, 새 비밀번호가 규칙을 어기면
     *     MEMBER_PASSWORD_POLICY, 현재 비밀번호와 같으면 MEMBER_PASSWORD_UNCHANGED
     */
    public void change(PasswordChangeCommand command) {
        Member member = findChangeableMember(command.memberId());
        rejectIfLocked(member.getEmail());
        verifyCurrentPassword(member, command);
        Password newPassword = Password.of(command.newPassword());
        if (passwordEncoder.matches(newPassword.value(), member.getPasswordHash())) {
            throw new MemberException(MemberErrorCode.MEMBER_PASSWORD_UNCHANGED);
        }
        passwordChangeApplier.apply(member.getId(), newPassword);
        memberSessions.invalidateAllExcept(member.getId(), command.currentSessionId());
        log.info("password changed memberId={}", member.getId());
    }

    // 탈퇴하면 세션이 모두 지워지므로 로그인한 요청에서 이런 회원을 만나기는 어렵다.
    // 만나더라도 실패 기록에 쓸 이메일이 없으니, 기록 없이 현재 비밀번호 불일치로 거부한다.
    private Member findChangeableMember(long memberId) {
        return memberRepository
                .findById(memberId)
                .filter(found -> found.getStatus() != MemberStatus.WITHDRAWN && found.getPasswordHash() != null)
                .orElseThrow(() -> new MemberException(MemberErrorCode.MEMBER_CURRENT_PASSWORD_MISMATCH));
    }

    // 로그인 잠금과 같은 기준(이메일별 실패 기록)을 쓴다. 그래야 세션을 훔친 사람이 이 경로로 비밀번호를 무제한 추측하지 못한다.
    // 잠긴 동안의 시도는 비밀번호를 확인하지 않고 기록하지도 않는다. 기록하면 잠금이 끝나는 시각이 계속 뒤로 밀린다.
    private void rejectIfLocked(String email) {
        Optional<Duration> retryAfter = loginAttemptService.findRetryAfter(email);
        if (retryAfter.isPresent()) {
            throw new RateLimitedException(MemberErrorCode.LOGIN_LOCKED, retryAfter.get());
        }
    }

    private void verifyCurrentPassword(Member member, PasswordChangeCommand command) {
        if (passwordEncoder.matches(command.currentPassword(), member.getPasswordHash())) {
            return;
        }
        loginAttemptService.recordFailure(member.getId(), member.getEmail(), command.ip());
        throw new MemberException(MemberErrorCode.MEMBER_CURRENT_PASSWORD_MISMATCH);
    }
}
