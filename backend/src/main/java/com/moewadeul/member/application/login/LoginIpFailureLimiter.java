package com.moewadeul.member.application.login;

import static com.moewadeul.member.application.MemberGuards.requireRequestIp;

import com.moewadeul.common.error.RateLimitedException;
import com.moewadeul.common.ratelimit.IpTokenBucket;
import com.moewadeul.member.domain.MemberErrorCode;
import com.moewadeul.member.domain.login.LoginLockout;
import java.time.Clock;
import org.springframework.stereotype.Component;

/** 이메일을 바꿔 가며 시도하면 이메일별 잠금에 걸리지 않으므로, BCrypt와 DB 쓰기 전에 IP 단위로 실패한 로그인만 센다. */
@Component
public class LoginIpFailureLimiter {

    private final IpTokenBucket bucket;

    public LoginIpFailureLimiter(Clock clock) {
        this.bucket = new IpTokenBucket(LoginLockout.IP_FAILURE_CAPACITY, LoginLockout.IP_FAILURE_WINDOW, clock);
    }

    /** 시도를 시작할 때 토큰을 먼저 하나 쓴다. 실패 기록을 BCrypt 뒤로 미루면 그 사이에 몰린 요청이 모두 통과한다. */
    public void reserve(String ip) {
        requireRequestIp(ip);
        bucket.tryTake(ip).ifPresent(retryAfter -> {
            throw new RateLimitedException(MemberErrorCode.LOGIN_LOCKED, retryAfter);
        });
    }

    public void refund(String ip) {
        requireRequestIp(ip);
        bucket.giveBack(ip);
    }

    void clear() {
        bucket.clear();
    }
}
