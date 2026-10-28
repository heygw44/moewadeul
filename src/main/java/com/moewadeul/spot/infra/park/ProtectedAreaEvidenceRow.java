package com.moewadeul.spot.infra.park;

import java.time.LocalDate;

public record ProtectedAreaEvidenceRow(String name, String source, LocalDate sourceDate) {}
