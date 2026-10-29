package com.moewadeul.trust.application.sanction;

import com.moewadeul.common.error.BusinessException;
import com.moewadeul.common.error.EnumParser;
import com.moewadeul.common.transaction.OutsideTransaction;
import com.moewadeul.trust.domain.sanction.SanctionLiftReason;
import com.moewadeul.trust.domain.sanction.SanctionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class SanctionLiftService {

    private final SanctionRepository sanctionRepository;
    private final SanctionLiftApplier sanctionLiftApplier;

    public SanctionLiftResult lift(long sanctionId, String reason, long adminId) {
        SanctionLiftReason liftReason = EnumParser.parse(
                SanctionLiftReason.class,
                reason,
                "해제 사유는 " + EnumParser.names(SanctionLiftReason.class) + " 중 하나여야 해요.");
        OutsideTransaction.require("해제 트랜잭션의 읽기 스냅숏이 회원 잠금 뒤에 정해져야 남은 정지를 빠짐없이 읽습니다.");
        long memberId = sanctionRepository.findMemberIdById(sanctionId).orElseThrow(BusinessException::notFound);
        return sanctionLiftApplier.apply(memberId, sanctionId, liftReason, adminId);
    }
}
