package com.moewadeul.spot.infra.park;

import com.moewadeul.spot.application.park.ProtectedAreaSource;
import java.time.LocalDate;

public record ProtectedAreaEvidenceRow(String name, ProtectedAreaSource source, LocalDate sourceDate) {}
