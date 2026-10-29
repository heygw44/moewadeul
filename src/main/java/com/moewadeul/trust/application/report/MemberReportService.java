package com.moewadeul.trust.application.report;

import com.moewadeul.common.transaction.OutsideTransaction;
import com.moewadeul.member.application.MemberSessions;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class MemberReportService {

    private final MemberReportApplier memberReportApplier;
    private final MemberSessions memberSessions;

    public long report(long reporterId, MemberReportCommand command) {
        OutsideTransaction.require("제재·신고 처리가 커밋된 뒤에 세션을 지워야 하고, 세션 삭제는 트랜잭션 안에서 부르면 커넥션을 하나 더 잡는다.");
        MemberReportResult result = memberReportApplier.apply(reporterId, command);
        if (result.isTargetSuspended()) {
            memberSessions.invalidateAll(result.targetMemberId());
            log.info(
                    "member temporarily suspended memberId={} reportId={}", result.targetMemberId(), result.reportId());
        }
        return result.reportId();
    }
}
