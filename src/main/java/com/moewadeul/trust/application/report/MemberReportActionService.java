package com.moewadeul.trust.application.report;

import com.moewadeul.common.error.BusinessException;
import com.moewadeul.common.error.CommonErrorCode;
import com.moewadeul.member.application.MemberSessions;
import com.moewadeul.trust.domain.sanction.SanctionType;
import java.util.Arrays;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 이 클래스에는 {@code @Transactional}을 두지 않는다. Spring Session JDBC는 세션 작업마다 새 트랜잭션(두 번째 커넥션)을 열어서,
 * 트랜잭션 안에서 세션을 지우면 동시 요청이 커넥션 풀 크기에 닿을 때 서로 커넥션을 기다리며 멈춘다.
 */
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
