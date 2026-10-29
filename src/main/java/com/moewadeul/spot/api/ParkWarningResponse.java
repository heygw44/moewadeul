package com.moewadeul.spot.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.moewadeul.spot.application.SpotParkWarning;
import com.moewadeul.spot.application.park.ProtectedAreaSource;
import java.time.LocalDate;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ParkWarningResponse(
        boolean warned,
        String areaName,
        ProtectedAreaSource source,
        LocalDate sourceDate,
        String notice,
        String guide) {

    public static ParkWarningResponse from(SpotParkWarning warning) {
        return new ParkWarningResponse(
                warning.warned(),
                warning.areaName(),
                warning.source(),
                warning.sourceDate(),
                warning.notice(),
                warning.guide());
    }
}
