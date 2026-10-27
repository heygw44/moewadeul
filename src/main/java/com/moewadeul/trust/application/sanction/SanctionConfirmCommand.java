package com.moewadeul.trust.application.sanction;

import com.moewadeul.trust.domain.sanction.SanctionType;

public record SanctionConfirmCommand(
        long targetMemberId, Long reportId, SanctionType type, String reason, long adminId) {}
