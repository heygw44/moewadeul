package com.moewadeul.publicdata.infra.forest;

import java.time.LocalDate;
import java.util.List;

public record ForestFile(LocalDate sourceDate, List<ForestRecord> records) {

    public ForestFile {
        records = List.copyOf(records);
    }
}
