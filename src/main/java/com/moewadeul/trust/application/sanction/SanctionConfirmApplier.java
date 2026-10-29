package com.moewadeul.trust.application.sanction;

import com.moewadeul.common.event.TrustEvents;
import com.moewadeul.common.outbox.OutboxEventRecorder;
import com.moewadeul.member.application.LockedMember;
import com.moewadeul.member.application.MemberSuspensionService;
import com.moewadeul.member.application.MemberWithdrawalService;
import com.moewadeul.trust.domain.identity.IdentityVerificationRepository;
import com.moewadeul.trust.domain.sanction.Sanction;
import com.moewadeul.trust.domain.sanction.SanctionPolicy;
import com.moewadeul.trust.domain.sanction.SanctionRepository;
import com.moewadeul.trust.domain.sanction.SanctionType;
import java.time.Clock;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SanctionConfirmApplier {

    private final SanctionRepository sanctionRepository;
    private final MemberSuspensionService memberSuspensionService;
    private final MemberWithdrawalService memberWithdrawalService;
    private final IdentityVerificationRepository identityVerificationRepository;
    private final OutboxEventRecorder outboxEventRecorder;
    private final Clock clock;

    @Transactional
    public SanctionConfirmResult apply(SanctionConfirmCommand command) {
        LockedMember member = memberSuspensionService.lockForSanction(command.targetMemberId());
        long memberId = member.id();
        Instant now = clock.instant();
        Integer highestLevel = sanctionRepository
                .findHighestConfirmedLevel(memberId)
                .map(Byte::intValue)
                .orElse(null);
        int level = SanctionPolicy.decideLevel(highestLevel, command.type());
        Sanction sanction = sanctionRepository.save(Sanction.confirm(
                memberId, command.reportId(), command.type(), level, command.reason(), now, command.adminId()));
        extendCiRetentionIfWithdrawn(memberId, now);
        boolean suspended = command.type().isSuspension();
        if (suspended) {
            suspend(member, sanction);
            outboxEventRecorder.record(
                    TrustEvents.SANCTION_BASECAMP_CLEANUP,
                    TrustEvents.MEMBER_AGGREGATE_TYPE,
                    memberId,
                    new TrustEvents.SanctionPayload(memberId, sanction.getId()));
            outboxEventRecorder.record(
                    TrustEvents.SANCTION_PROGRAM_CLEANUP,
                    TrustEvents.MEMBER_AGGREGATE_TYPE,
                    memberId,
                    new TrustEvents.SanctionPayload(memberId, sanction.getId()));
        }
        outboxEventRecorder.record(
                TrustEvents.SANCTION_CONFIRMED,
                TrustEvents.MEMBER_AGGREGATE_TYPE,
                memberId,
                new TrustEvents.SanctionConfirmedPayload(
                        memberId, sanction.getId(), sanction.getType().name(), sanction.getEndsAt()));
        return new SanctionConfirmResult(sanction.getId(), memberId, suspended);
    }

    private void suspend(LockedMember member, Sanction sanction) {
        if (sanction.getType() == SanctionType.PERMANENT) {
            memberSuspensionService.suspendPermanently(member);
            return;
        }
        memberSuspensionService.suspendTemporarily(member, sanction.getEndsAt());
    }

    private void extendCiRetentionIfWithdrawn(long memberId, Instant now) {
        memberWithdrawalService
                .findWithdrawnAt(memberId)
                .ifPresent(withdrawnAt -> identityVerificationRepository
                        .findByMemberId(memberId)
                        .ifPresent(identity -> identity.extendRetentionForSanction(withdrawnAt, now)));
    }
}
