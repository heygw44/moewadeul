package com.moewadeul.spot.application;

import java.time.LocalDate;
import java.util.Objects;

public record SpotClosure(PublicSpotOperatingStatus operatingStatus, LocalDate closedFrom, LocalDate closedUntil) {

    public boolean isClosedOn(LocalDate today) {
        Objects.requireNonNull(today, "판단할 날짜가 없습니다.");
        if (closedFrom != null || closedUntil != null) {
            boolean started = closedFrom == null || !today.isBefore(closedFrom);
            boolean notEnded = closedUntil == null || !today.isAfter(closedUntil);
            return started && notEnded;
        }
        return operatingStatus == PublicSpotOperatingStatus.TEMPORARILY_CLOSED
                || operatingStatus == PublicSpotOperatingStatus.PERMANENTLY_CLOSED;
    }
}
