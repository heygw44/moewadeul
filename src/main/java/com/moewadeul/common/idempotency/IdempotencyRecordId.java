package com.moewadeul.common.idempotency;

import java.io.Serializable;

public record IdempotencyRecordId(long memberId, String idempotencyKey) implements Serializable {}
