package com.moewadeul.common.idempotency;

public record IdempotentRequest(long memberId, String key, String operation, Object payload) {}
