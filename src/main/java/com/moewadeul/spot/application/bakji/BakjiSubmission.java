package com.moewadeul.spot.application.bakji;

import com.moewadeul.spot.application.SpotParkWarning;
import java.util.List;

public record BakjiSubmission(
        long spotId,
        SpotParkWarning parkWarning,
        String guide,
        String notice,
        List<BakjiDuplicateCandidate> duplicateCandidates) {

    public BakjiSubmission {
        duplicateCandidates = List.copyOf(duplicateCandidates);
    }
}
