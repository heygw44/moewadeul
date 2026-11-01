package com.moewadeul.idempotency.domain;

import java.io.Serializable;

public record IdempotencyRecordId(long memberId, String idempotencyKey) implements Serializable {}
