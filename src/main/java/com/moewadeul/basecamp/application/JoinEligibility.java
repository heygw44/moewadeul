package com.moewadeul.basecamp.application;

import com.moewadeul.basecamp.domain.JoinUnmetReason;
import java.util.List;

public record JoinEligibility(boolean canApply, List<JoinUnmetReason> unmetReasons) {

    public static JoinEligibility of(List<JoinUnmetReason> unmetReasons) {
        return new JoinEligibility(unmetReasons.isEmpty(), unmetReasons);
    }
}
