package com.moewadeul.spot.infra.park;

import java.time.LocalDate;

public record ProtectedAreaInsertRow(
        String source, String areaType, String name, LocalDate sourceDate, String boundaryWkt) {}
