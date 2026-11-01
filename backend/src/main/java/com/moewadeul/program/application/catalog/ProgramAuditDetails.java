package com.moewadeul.program.application.catalog;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

public final class ProgramAuditDetails {

    private ProgramAuditDetails() {}

    public record Create(int capacity, boolean overnight) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Update(List<String> changedFields, Integer capacityBefore, Integer capacityAfter) {}

    public record Cancel(String fromStatus, String toStatus, int canceledApplicationCount, int refundedPaymentCount) {}
}
