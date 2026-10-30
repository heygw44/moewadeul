package com.moewadeul.trust.application.report;

import com.moewadeul.common.error.EnumParser;
import com.moewadeul.common.transaction.OutsideTransaction;
import com.moewadeul.member.application.MemberSessionCleanup;
import com.moewadeul.trust.domain.sanction.SanctionType;
import java.util.EnumSet;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class MemberReportActionService {

    private static final Set<SanctionType> ADMIN_SANCTION_TYPES =
            EnumSet.complementOf(EnumSet.of(SanctionType.TEMPORARY_72H));
    private static final String SANCTION_TYPE_MESSAGE = "관리자가 내릴 수 없는 제재 종류예요.";

    private final MemberReportActionApplier memberReportActionApplier;
    private final MemberSessionCleanup memberSessionCleanup;

    public MemberReportActionResult act(MemberReportActionCommand command) {
        OutsideTransaction.require("제재·신고 처리가 커밋된 뒤에 세션을 지워야 하고, 세션 삭제는 트랜잭션 안에서 부르면 커넥션을 하나 더 잡는다.");
        MemberReportActionResult result = memberReportActionApplier.apply(command, parseSanctionType(command));
        if (result.suspended()) {
            memberSessionCleanup.invalidateAfterCommit(result.targetMemberId());
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
        return EnumParser.parseAmong(ADMIN_SANCTION_TYPES, command.sanctionType(), SANCTION_TYPE_MESSAGE);
    }
}
