package com.moewadeul.trust.application.sanction;

import com.moewadeul.trust.domain.sanction.SanctionType;

public record SanctionConfirmCommand(
        long targetMemberId, long reportId, SanctionType type, String reason, long adminId) {}
