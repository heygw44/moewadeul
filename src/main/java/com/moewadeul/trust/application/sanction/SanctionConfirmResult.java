package com.moewadeul.trust.application.sanction;

public record SanctionConfirmResult(long sanctionId, long memberId, boolean suspended) {}
