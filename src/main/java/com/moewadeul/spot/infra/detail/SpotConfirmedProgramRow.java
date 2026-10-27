package com.moewadeul.spot.infra.detail;

import java.time.Instant;

public record SpotConfirmedProgramRow(Instant startAt, Instant endAt, int headcount) {}
