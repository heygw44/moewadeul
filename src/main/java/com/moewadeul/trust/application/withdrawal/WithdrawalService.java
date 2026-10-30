package com.moewadeul.trust.application.withdrawal;

import com.moewadeul.common.transaction.OutsideTransaction;
import com.moewadeul.member.application.MemberSessionCleanup;
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
    private final MemberSessionCleanup memberSessionCleanup;

    public void withdraw(long memberId, String password, String ip) {
        OutsideTransaction.require("탈퇴가 커밋된 뒤에 세션을 지워야 한다.");
        memberWithdrawalService.verifyPasswordUnlessWithdrawn(memberId, password, ip);
        boolean withdrawn = withdrawalApplier.apply(memberId);
        memberSessionCleanup.invalidateAfterCommit(memberId);
        if (withdrawn) {
            log.info("member withdrawn memberId={}", memberId);
        }
    }
}
