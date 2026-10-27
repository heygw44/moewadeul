package com.moewadeul.publicdata.infra.park;

import java.time.LocalDate;
import java.util.List;

public record ParkBoundaryFile(LocalDate sourceDate, List<ParkBoundaryRecord> records) {

    public ParkBoundaryFile {
        records = List.copyOf(records);
    }
}
