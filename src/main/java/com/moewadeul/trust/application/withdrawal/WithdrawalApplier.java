package com.moewadeul.trust.application.withdrawal;

import com.moewadeul.common.outbox.OutboxEventRecorder;
import com.moewadeul.member.application.MemberWithdrawalService;
import com.moewadeul.trust.domain.identity.IdentityVerification;
import com.moewadeul.trust.domain.identity.IdentityVerificationRepository;
import com.moewadeul.trust.domain.sanction.SanctionRepository;
import com.moewadeul.trust.domain.sanction.SanctionStatus;
import java.time.Clock;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class WithdrawalApplier {

    private final MemberWithdrawalService memberWithdrawalService;
    private final IdentityVerificationRepository identityVerificationRepository;
    private final SanctionRepository sanctionRepository;
    private final OutboxEventRecorder outboxEventRecorder;
    private final Clock clock;

    @Transactional
    public void apply(long memberId) {
        memberWithdrawalService.withdraw(memberId);
        Instant now = clock.instant();
        identityVerificationRepository.findByMemberId(memberId).ifPresent(identity -> {
            boolean hasSanctionHistory =
                    sanctionRepository.existsByMemberIdAndStatusNot(memberId, SanctionStatus.LIFTED);
            identity.withdraw(IdentityVerification.ciRetainedUntil(now, hasSanctionHistory), now);
        });
        recordCleanup(WithdrawalEvents.BASECAMP_CLEANUP_EVENT_TYPE, memberId);
        recordCleanup(WithdrawalEvents.PROGRAM_CLEANUP_EVENT_TYPE, memberId);
        recordCleanup(WithdrawalEvents.MEMORY_CLEANUP_EVENT_TYPE, memberId);
    }

    private void recordCleanup(String eventType, long memberId) {
        outboxEventRecorder.record(
                eventType, WithdrawalEvents.AGGREGATE_TYPE, memberId, new WithdrawalEvents.Payload(memberId));
    }
}
