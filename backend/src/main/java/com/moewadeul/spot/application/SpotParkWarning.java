package com.moewadeul.spot.application;

import com.moewadeul.spot.application.park.ProtectedAreaSource;
import java.time.LocalDate;

public record SpotParkWarning(
        boolean warned,
        String areaName,
        ProtectedAreaSource source,
        LocalDate sourceDate,
        String notice,
        String guide) {

    public static SpotParkWarning notWarned() {
        return new SpotParkWarning(false, null, null, null, null, null);
    }

    public static SpotParkWarning warned(String areaName, ProtectedAreaSource source, LocalDate sourceDate) {
        return new SpotParkWarning(
                true, areaName, source, sourceDate, ParkWarningTexts.NOTICE, ParkWarningTexts.WARNED_GUIDE);
    }
}
