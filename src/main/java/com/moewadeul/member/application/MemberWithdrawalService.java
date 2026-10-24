package com.moewadeul.member.application;

import com.moewadeul.common.error.RateLimitedException;
import com.moewadeul.member.domain.Member;
import com.moewadeul.member.domain.MemberErrorCode;
import com.moewadeul.member.domain.MemberException;
import com.moewadeul.member.domain.MemberRepository;
import com.moewadeul.member.domain.MemberStatus;
import com.moewadeul.member.infra.password.PasswordResetTokenMapper;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 다른 모듈이 회원 탈퇴를 처리할 때 부르는 서비스다. 세션 삭제는 하지 않는다.
 * 세션 삭제는 DB 트랜잭션 밖에서 해야 해서, 이 서비스를 부른 쪽이 트랜잭션이 끝난 뒤에 {@link MemberSessions}로 한다.
 */
@Service
@RequiredArgsConstructor
public class MemberWithdrawalService {

    private final MemberRepository memberRepository;
    private final LoginAttemptService loginAttemptService;
    private final PasswordEncoder passwordEncoder;
    private final PasswordResetTokenMapper passwordResetTokenMapper;
    private final Clock clock;

    /**
     * 호출하면 memberId인 회원의 비밀번호가 password와 맞는지 확인하고, 틀리면 {@code LOGIN_FAILED}로 거부한다.
     * 회원이 없거나 이미 탈퇴했거나 비밀번호 해시가 없을 때도 같은 예외를 던져서 이유를 구분해 알리지 않는다.
     *
     * <p>틀린 시도는 로그인 실패로 기록해서 로그인 잠금 계산에 넣는다. 이미 로그인한 회원의 확인이지만, 세션을 훔친 사람이
     * 이 경로로 비밀번호를 무제한 추측하지 못하게 하려는 것이다. 비밀번호 변경과 같은 기준이다.
     *
     * <p>이 메서드에는 {@code @Transactional}을 두지 않는다. BCrypt 비교가 느려서 트랜잭션 안에서 하면 DB 커넥션을 오래 쥐기 때문이다.
     * 또 실패를 기록한 뒤 예외를 던지므로, 트랜잭션 안이면 실패 기록도 함께 롤백되어 잠금이 걸리지 않는다.
     *
     * @param ip 실패 기록에 남길 요청 IP
     * @throws RateLimitedException 로그인 실패가 쌓여 잠겨 있으면 LOGIN_LOCKED
     */
    public void verifyPassword(long memberId, String password, String ip) {
        Member member = findWithdrawableMember(memberId);
        rejectIfLocked(member.getEmail());
        boolean matched = passwordEncoder.matches(password, member.getPasswordHash());
        // 비교는 트랜잭션 밖에서 하므로 그사이 같은 이메일의 다른 요청이 실패를 쌓아 잠겼을 수 있다.
        // 그래서 서비스는 비밀번호가 맞았어도 잠겨 있으면 탈퇴하지 않고 LOGIN_LOCKED로 거부한다.
        loginAttemptService.settle(member.getId(), member.getEmail(), ip, matched);
        if (!matched) {
            throw new MemberException(MemberErrorCode.LOGIN_FAILED);
        }
    }

    // 탈퇴하면 세션이 모두 지워지므로 로그인한 요청에서 이런 회원을 만나기는 어렵다.
    // 만나더라도 실패 기록에 쓸 이메일이 없으니, 기록 없이 LOGIN_FAILED로 거부한다.
    private Member findWithdrawableMember(long memberId) {
        return memberRepository
                .findById(memberId)
                .filter(found -> found.getStatus() != MemberStatus.WITHDRAWN && found.getPasswordHash() != null)
                .orElseThrow(() -> new MemberException(MemberErrorCode.LOGIN_FAILED));
    }

    // 잠긴 동안의 시도는 비밀번호를 확인하지 않고 기록하지도 않는다. 기록하면 잠금이 끝나는 시각이 계속 뒤로 밀린다.
    private void rejectIfLocked(String email) {
        Optional<Duration> retryAfter = loginAttemptService.findRetryAfter(email);
        if (retryAfter.isPresent()) {
            throw new RateLimitedException(MemberErrorCode.LOGIN_LOCKED, retryAfter.get());
        }
    }

    /**
     * 호출하면 memberId인 회원의 행을 쓰기 잠금으로 읽고 탈퇴 처리한 뒤, 그 회원의 사용하지 않은 비밀번호 재설정 토큰을 모두 무효로 만든다.
     * 토큰이 남아 있으면 탈퇴한 회원의 행에 비밀번호 해시가 다시 생기기 때문이다. 반드시 호출한 쪽의 트랜잭션 안에서 불러야 하고,
     * 트랜잭션이 없으면 예외가 난다. 같은 트랜잭션에서 제재 확정과 같은 순서로 회원 행을 가장 먼저 잠그라는 뜻이다.
     * 회원이 없거나 이미 탈퇴했으면 {@code LOGIN_FAILED}로 거부한다. 이 경우 두 탈퇴 요청이 겹쳤을 때 늦은 쪽이 이미 끝난 탈퇴를 다시 하지 않게 한다.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void withdraw(long memberId) {
        Member member = memberRepository.findByIdForUpdate(memberId).orElse(null);
        if (member == null || member.getStatus() == MemberStatus.WITHDRAWN) {
            throw new MemberException(MemberErrorCode.LOGIN_FAILED);
        }
        Instant now = clock.instant();
        member.withdraw(now);
        passwordResetTokenMapper.invalidateUnusedTokens(memberId, now);
    }

    /**
     * 호출하면 memberId인 회원이 탈퇴했을 때 그 탈퇴 시각을 돌려준다. 회원이 없거나 탈퇴하지 않았으면 비어 있다. 잠금 없이 읽으므로,
     * 호출하는 쪽이 이미 회원 행을 잠갔을 때 쓴다.
     */
    @Transactional(readOnly = true)
    public Optional<Instant> findWithdrawnAt(long memberId) {
        return memberRepository
                .findById(memberId)
                .filter(member -> member.getStatus() == MemberStatus.WITHDRAWN)
                .map(Member::getWithdrawnAt);
    }
}
