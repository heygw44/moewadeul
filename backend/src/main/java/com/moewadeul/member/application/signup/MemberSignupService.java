package com.moewadeul.member.application.signup;

import com.moewadeul.common.transaction.OutsideTransaction;
import com.moewadeul.member.application.verification.EmailSendIpLimiter;
import com.moewadeul.member.domain.Email;
import com.moewadeul.member.domain.Nickname;
import com.moewadeul.member.domain.Password;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class MemberSignupService {

    private final MemberSignupApplier signupApplier;
    private final EmailSendIpLimiter emailSendIpLimiter;
    private final PasswordEncoder passwordEncoder;

    public SignupResult signUp(SignupCommand command) {
        OutsideTransaction.require("BCrypt를 계산하는 동안 IP 잠금 행과 커넥션을 쥐지 않아야 같은 IP의 가입이 해시 시간만큼 줄 서지 않는다.");
        Password password = Password.of(command.password());
        Email email = Email.of(command.email());
        Nickname nickname = new Nickname(command.nickname());
        signupApplier.rejectBeforeHashing(email, emailSendIpLimiter.peekSendsByIp(command.requestIp()));
        String passwordHash = passwordEncoder.encode(password.value());
        return signupApplier.signUp(email, nickname, passwordHash, command.requestIp());
    }
}
