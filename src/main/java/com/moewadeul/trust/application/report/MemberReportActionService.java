package com.moewadeul.trust.application.report;

import com.moewadeul.common.error.BusinessException;
import com.moewadeul.common.error.CommonErrorCode;
import com.moewadeul.member.application.MemberSessions;
import com.moewadeul.trust.domain.sanction.SanctionType;
import java.util.Arrays;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class MemberReportActionService {

    private final MemberReportActionApplier memberReportActionApplier;
    private final MemberSessions memberSessions;

    public MemberReportActionResult act(MemberReportActionCommand command) {
        MemberReportActionResult result = memberReportActionApplier.apply(command, parseSanctionType(command));
        if (result.suspended()) {
            memberSessions.invalidateAll(result.targetMemberId());
        }
        log.info(
                "member report actioned reportId={} adminId={} sanctionId={}",
                result.reportId(),
                command.adminId(),
                result.sanctionId());
        return result;
    }

    private static SanctionType parseSanctionType(MemberReportActionCommand command) {
        if (command.sanctionType() == null) {
            return null;
        }
        return Arrays.stream(SanctionType.values())
                .filter(type -> type != SanctionType.TEMPORARY_72H)
                .filter(type -> type.name().equals(command.sanctionType()))
                .findFirst()
                .orElseThrow(() -> new BusinessException(CommonErrorCode.INVALID_INPUT, "관리자가 내릴 수 없는 제재 종류예요."));
    }
}
