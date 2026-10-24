package com.moewadeul.member.application;

import com.moewadeul.common.error.RateLimitedException;
import com.moewadeul.member.application.login.MemberLoginService;
import com.moewadeul.member.domain.MemberErrorCode;
import com.moewadeul.member.domain.login.LoginHistory;
import com.moewadeul.member.domain.login.LoginHistoryRepository;
import com.moewadeul.member.domain.login.LoginLockout;
import com.moewadeul.member.infra.login.LoginAttemptLockMapper;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 로그인 시도 기록을 짧은 트랜잭션으로 읽고 쓴다.
 *
 * <p>{@link MemberLoginService#login}은 실패하면 예외를 던진다. 그 메서드 전체가 하나의 트랜잭션이면 예외 때문에 롤백되어
 * 실패 기록도 사라지고, 그러면 실패 횟수가 쌓이지 않아 잠금이 걸리지 않는다. 그래서 기록 쓰기는 이 별도 빈의 메서드마다
 * 따로 커밋한다. 비밀번호 해시 비교(BCrypt)도 DB 커넥션을 붙잡지 않도록 트랜잭션 밖에서 한다.
 *
 * <p>비교를 트랜잭션 밖에서 하므로 {@link #findRetryAfter}로 잠금을 확인한 뒤 실패를 기록하기까지 같은 이메일의 다른 요청이 끼어든다.
 * 그래서 요청 50건이 모두 아직 실패가 없다고 보고 통과해 10분에 5번을 넘게 시도할 수 있다. 호출하는 쪽은 비교를 마친 뒤
 * {@link #settle}로 잠금을 한 번 더 확인하고 기록한다. 이 메서드가 이메일별 잠금 행을 잡아 같은 이메일의 요청을 한 줄로 세운다.
 */
@Service
@RequiredArgsConstructor
public class LoginAttemptService {

    private final LoginHistoryRepository loginHistoryRepository;
    private final LoginAttemptLockMapper attemptLockMapper;
    private final Clock clock;

    /**
     * 호출하면 그 이메일이 잠겨 있을 때 잠금이 풀릴 때까지 남은 시간을 돌려준다. 잠겨 있지 않으면 빈 값이다.
     * 잠금 행을 잡지 않는 읽기라서, 이미 잠긴 이메일의 요청이 비밀번호 비교를 하기 전에 빨리 거르는 데만 쓴다.
     */
    @Transactional(readOnly = true)
    public Optional<Duration> findRetryAfter(String attemptedEmail) {
        return retryAfter(attemptedEmail, Instant.now(clock));
    }

    /**
     * 호출하면 비밀번호 비교를 마친 시도를 이메일별 잠금 행을 잡은 채 정산한다. 잠겨 있으면 비교 결과와 상관없이 예외를 던지고 아무것도
     * 기록하지 않는다. 잠긴 뒤에 끝난 비교가 맞았다고 알려 주면 잠금 동안 무차별 대입을 계속할 수 있기 때문이다.
     * 잠겨 있지 않고 비밀번호가 틀렸으면 실패를 기록한다. 맞았으면 아무것도 기록하지 않는다.
     *
     * @param memberId 이메일에 해당하는 회원이 없으면 null이다.
     * @throws RateLimitedException 이메일이 잠겨 있으면 LOGIN_LOCKED
     */
    @Transactional
    public void settle(Long memberId, String attemptedEmail, String ip, boolean passwordMatched) {
        Instant now = Instant.now(clock);
        attemptLockMapper.lock(LoginLockout.attemptKey(attemptedEmail), now);
        Optional<Duration> retryAfter = retryAfter(attemptedEmail, now);
        if (retryAfter.isPresent()) {
            throw new RateLimitedException(MemberErrorCode.LOGIN_LOCKED, retryAfter.get());
        }
        if (!passwordMatched) {
            loginHistoryRepository.save(LoginHistory.failure(memberId, attemptedEmail, ip, now));
        }
    }

    @Transactional
    public void recordSuccess(long memberId, String attemptedEmail, String ip) {
        loginHistoryRepository.save(LoginHistory.success(memberId, attemptedEmail, ip, Instant.now(clock)));
    }

    private Optional<Duration> retryAfter(String attemptedEmail, Instant now) {
        List<Instant> failureTimes =
                loginHistoryRepository.findFailureTimesSinceLastSuccess(attemptedEmail, LoginLockout.windowStart(now));
        return LoginLockout.lockedUntil(failureTimes, now).map(lockedUntil -> Duration.between(now, lockedUntil));
    }
}
