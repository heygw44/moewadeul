package com.moewadeul.spot.infra.park;

import java.time.LocalDate;

public record ProtectedAreaUpdateRow(long id, LocalDate sourceDate, String boundaryWkt) {}
