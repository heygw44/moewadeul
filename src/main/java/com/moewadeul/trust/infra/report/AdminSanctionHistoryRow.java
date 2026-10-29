package com.moewadeul.trust.infra.report;

import com.moewadeul.trust.domain.sanction.SanctionStatus;
import com.moewadeul.trust.domain.sanction.SanctionType;
import java.time.Instant;

public record AdminSanctionHistoryRow(
        long sanctionId,
        SanctionType type,
        Integer level,
        SanctionStatus status,
        String reason,
        Instant startsAt,
        Instant endsAt,
        Instant liftedAt) {}
