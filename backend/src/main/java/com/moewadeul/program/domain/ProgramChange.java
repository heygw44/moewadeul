package com.moewadeul.program.domain;

import java.util.List;

public record ProgramChange(List<String> changedFields, int capacityBefore, int capacityAfter) {

    public boolean isCapacityChanged() {
        return capacityBefore != capacityAfter;
    }
}
