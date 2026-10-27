package com.moewadeul.publicdata.infra.forest;

import java.util.List;

public record ForestStandardFile(List<ForestStandardRecord> records) {

    public ForestStandardFile {
        records = List.copyOf(records);
    }
}
