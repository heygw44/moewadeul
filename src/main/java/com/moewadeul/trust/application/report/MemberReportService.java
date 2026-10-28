package com.moewadeul.trust.application.report;

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
        MemberReportResult result = memberReportApplier.apply(reporterId, command);
        if (result.isTargetSuspended()) {
            memberSessions.invalidateAll(result.targetMemberId());
            log.info(
                    "member temporarily suspended memberId={} reportId={}", result.targetMemberId(), result.reportId());
        }
        return result.reportId();
    }
}
