package com.moewadeul.spot.domain.bakji;

import java.io.Serializable;

public record BakjiConfirmationId(long spotId, long memberId) implements Serializable {}
