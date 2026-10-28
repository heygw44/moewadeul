package com.moewadeul.trust.application.withdrawal;

import com.moewadeul.member.application.MemberSessions;
import com.moewadeul.member.application.MemberWithdrawalService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class WithdrawalService {

    private final MemberWithdrawalService memberWithdrawalService;
    private final WithdrawalApplier withdrawalApplier;
    private final MemberSessions memberSessions;

    public void withdraw(long memberId, String password, String ip) {
        memberWithdrawalService.verifyPassword(memberId, password, ip);
        withdrawalApplier.apply(memberId);
        memberSessions.invalidateAll(memberId);
        log.info("member withdrawn memberId={}", memberId);
    }
}
