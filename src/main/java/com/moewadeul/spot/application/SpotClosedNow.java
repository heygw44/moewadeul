package com.moewadeul.spot.application;

import com.moewadeul.spot.domain.SpotType;
import java.time.LocalDate;

public final class SpotClosedNow {

    private SpotClosedNow() {}

    public static boolean isClosedOn(
            SpotType type,
            PublicSpotOperatingStatus operatingStatus,
            LocalDate closedFrom,
            LocalDate closedUntil,
            LocalDate today) {
        if (type == SpotType.BAKJI) {
            return false;
        }
        SpotClosure closure = new SpotClosure(operatingStatus, closedFrom, closedUntil);
        return closure.isClosedOn(today);
    }
}
