package com.moewadeul.program.application.enrollment;

import com.moewadeul.common.transaction.OutsideTransaction;
import com.moewadeul.program.domain.ProgramApplicationRepository;
import com.moewadeul.program.domain.ProgramCancelReason;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProgramSanctionCleanupService {

    private final ProgramApplicationRepository applicationRepository;
    private final ProgramSanctionCleanupApplier applier;

    public void cleanUp(long memberId, ProgramCancelReason reason) {
        OutsideTransaction.require("신청마다 따로 커밋해야 한 건의 실패가 나머지 정리를 되돌리지 않는다.");
        List<Long> applicationIds = applicationRepository.findPendingPaymentIdsByMember(memberId);
        List<Long> failedApplicationIds = new ArrayList<>();
        int canceledCount = 0;
        for (long applicationId : applicationIds) {
            try {
                if (applier.cancelPending(applicationId, memberId, reason)) {
                    canceledCount++;
                }
            } catch (RuntimeException e) {
                log.error(
                        "program cleanup failed reason={} memberId={} applicationId={}",
                        reason,
                        memberId,
                        applicationId,
                        e);
                failedApplicationIds.add(applicationId);
            }
        }
        int reviewRequestedCount = 0;
        boolean reviewFailed = false;
        try {
            reviewRequestedCount = applier.requestReview(memberId);
        } catch (RuntimeException e) {
            log.error("program review request failed reason={} memberId={}", reason, memberId, e);
            reviewFailed = true;
        }
        log.info(
                "program cleanup reason={} memberId={} targets={} canceled={} reviewRequested={} failed={}",
                reason,
                memberId,
                applicationIds.size(),
                canceledCount,
                reviewRequestedCount,
                failedApplicationIds.size());
        if (!failedApplicationIds.isEmpty() || reviewFailed) {
            throw new IllegalStateException("행사 신청 정리에 실패한 항목이 있습니다. reason=" + reason + " applicationIds="
                    + failedApplicationIds + " reviewFailed=" + reviewFailed);
        }
    }
}
