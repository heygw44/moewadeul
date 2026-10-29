package com.moewadeul.spot.infra.park;

import com.moewadeul.spot.application.park.ProtectedAreaSource;
import com.moewadeul.spot.application.park.ProtectedAreaType;
import java.time.LocalDate;

public record ProtectedAreaInsertRow(
        ProtectedAreaSource source,
        ProtectedAreaType areaType,
        String name,
        LocalDate sourceDate,
        String boundaryWkt) {}
