package com.moewadeul.spot.api.bakji;

import com.moewadeul.spot.application.bakji.BakjiDuplicateCandidate;

public record DuplicateCandidateResponse(long spotId, String name, int distanceM) {

    static DuplicateCandidateResponse from(BakjiDuplicateCandidate candidate) {
        return new DuplicateCandidateResponse(candidate.spotId(), candidate.name(), candidate.distanceM());
    }
}
