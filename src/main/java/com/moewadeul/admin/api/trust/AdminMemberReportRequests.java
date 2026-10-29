package com.moewadeul.admin.api.trust;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public final class AdminMemberReportRequests {

    private AdminMemberReportRequests() {}

    public record ActionRequest(
            @Valid SanctionRequest sanction,
            Boolean hideReview,
            @Size(max = 1000) String note) {

        public boolean shouldHideReview() {
            return Boolean.TRUE.equals(hideReview);
        }
    }

    public record SanctionRequest(
            @NotBlank String type,
            @NotBlank @Size(max = 500) String reason) {}

    public record DismissRequest(@Size(max = 1000) String note) {}
}
