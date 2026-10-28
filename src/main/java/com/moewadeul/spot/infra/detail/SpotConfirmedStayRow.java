package com.moewadeul.spot.infra.detail;

import java.time.LocalDate;

public record SpotConfirmedStayRow(LocalDate startDate, LocalDate endDate, int headcount) {}
