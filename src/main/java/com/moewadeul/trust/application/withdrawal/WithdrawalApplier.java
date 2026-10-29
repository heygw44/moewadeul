package com.moewadeul.trust.application.withdrawal;

import com.moewadeul.common.event.TrustEvents;
import com.moewadeul.common.outbox.OutboxEventRecorder;
import com.moewadeul.member.application.MemberWithdrawalService;
import com.moewadeul.trust.domain.identity.IdentityVerification;
import com.moewadeul.trust.domain.identity.IdentityVerificationRepository;
import com.moewadeul.trust.domain.sanction.SanctionRepository;
import java.time.Clock;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
class WithdrawalApplier {

    private final MemberWithdrawalService memberWithdrawalService;
    private final IdentityVerificationRepository identityVerificationRepository;
    private final SanctionRepository sanctionRepository;
    private final OutboxEventRecorder outboxEventRecorder;
    private final Clock clock;

    @Transactional
    void apply(long memberId) {
        memberWithdrawalService.lockAndWithdraw(memberId);
        Instant now = clock.instant();
        identityVerificationRepository.findByMemberId(memberId).ifPresent(identity -> {
            boolean retainsCi = sanctionRepository.existsCiRetentionSanction(memberId);
            identity.withdraw(IdentityVerification.ciRetainedUntil(now, retainsCi), now);
        });
        recordCleanup(TrustEvents.WITHDRAWAL_BASECAMP_CLEANUP, memberId);
        recordCleanup(TrustEvents.WITHDRAWAL_PROGRAM_CLEANUP, memberId);
        recordCleanup(TrustEvents.WITHDRAWAL_MEMORY_CLEANUP, memberId);
    }

    private void recordCleanup(String eventType, long memberId) {
        outboxEventRecorder.record(
                eventType, TrustEvents.MEMBER_AGGREGATE_TYPE, memberId, new TrustEvents.WithdrawalPayload(memberId));
    }
}
