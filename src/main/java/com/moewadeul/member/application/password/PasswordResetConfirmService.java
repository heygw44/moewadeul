package com.moewadeul.member.application.password;

import com.moewadeul.common.transaction.OutsideTransaction;
import com.moewadeul.member.application.MemberSessions;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class PasswordResetConfirmService {

    private final PasswordResetApplier passwordResetApplier;
    private final MemberSessions memberSessions;

    public void confirm(String rawToken, String newPassword) {
        OutsideTransaction.require("비밀번호 변경이 커밋된 뒤에 세션을 지워야 한다.");
        long memberId = passwordResetApplier.apply(rawToken, newPassword);
        memberSessions.invalidateAll(memberId);
        log.info("password reset memberId={}", memberId);
    }
}
