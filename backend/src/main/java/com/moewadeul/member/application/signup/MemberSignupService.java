package com.moewadeul.member.application.signup;

import com.moewadeul.common.error.BusinessException;
import com.moewadeul.common.error.CommonErrorCode;
import com.moewadeul.common.transaction.OutsideTransaction;
import com.moewadeul.member.application.verification.EmailSendIpLimiter;
import com.moewadeul.member.domain.Email;
import com.moewadeul.member.domain.Nickname;
import com.moewadeul.member.domain.Password;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class MemberSignupService {

    // CPU 2개 서버에서 BCrypt가 한꺼번에 돌면 지도 같은 다른 요청이 CPU를 기다린다. 그래서 가입은 한 번에 둘만 처리한다.
    // 줄 선 요청은 차례가 온 뒤에 IP 한도를 확인하므로, 그사이 한도가 찬 요청은 해시를 계산하지 않고 거절된다.
    static final int MAX_CONCURRENT_SIGNUPS = 2;
    private static final long PERMIT_WAIT_SECONDS = 5;

    private final Semaphore signupPermits = new Semaphore(MAX_CONCURRENT_SIGNUPS, true);

    private final MemberSignupApplier signupApplier;
    private final EmailSendIpLimiter emailSendIpLimiter;
    private final PasswordEncoder passwordEncoder;

    public SignupResult signUp(SignupCommand command) {
        OutsideTransaction.require("BCrypt를 계산하는 동안 IP 잠금 행과 커넥션을 쥐지 않아야 같은 IP의 가입이 해시 시간만큼 줄 서지 않는다.");
        Password password = Password.of(command.password());
        Email email = Email.of(command.email());
        Nickname nickname = new Nickname(command.nickname());
        acquirePermit();
        try {
            signupApplier.rejectBeforeHashing(email, emailSendIpLimiter.peekSendsByIp(command.requestIp()));
            String passwordHash = passwordEncoder.encode(password.value());
            return signupApplier.signUp(email, nickname, passwordHash, command.requestIp());
        } finally {
            signupPermits.release();
        }
    }

    private void acquirePermit() {
        try {
            if (signupPermits.tryAcquire(PERMIT_WAIT_SECONDS, TimeUnit.SECONDS)) {
                return;
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        throw new BusinessException(CommonErrorCode.RESOURCE_BUSY);
    }
}
