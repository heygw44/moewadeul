package com.moewadeul.trust.infra.report;

import java.time.Instant;

public record AdminSanctionHistoryRow(
        long sanctionId,
        String type,
        Integer level,
        String status,
        String reason,
        Instant startsAt,
        Instant endsAt,
        Instant liftedAt) {}
