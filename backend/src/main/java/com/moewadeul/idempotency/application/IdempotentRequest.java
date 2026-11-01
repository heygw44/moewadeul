package com.moewadeul.idempotency.application;

public record IdempotentRequest(long memberId, String key, String operation, Object payload) {}
