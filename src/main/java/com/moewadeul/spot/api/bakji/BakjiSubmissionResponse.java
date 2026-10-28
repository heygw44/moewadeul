package com.moewadeul.spot.api.bakji;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.moewadeul.spot.api.ParkWarningResponse;
import com.moewadeul.spot.application.bakji.BakjiSubmission;
import java.util.List;

public record BakjiSubmissionResponse(
        long spotId,
        ParkWarningResponse parkWarning,
        String guide,
        @JsonInclude(JsonInclude.Include.NON_NULL) String notice,
        List<DuplicateCandidateResponse> duplicateCandidates) {

    static BakjiSubmissionResponse from(BakjiSubmission submission) {
        return new BakjiSubmissionResponse(
                submission.spotId(),
                ParkWarningResponse.from(submission.parkWarning()),
                submission.guide(),
                submission.notice(),
                submission.duplicateCandidates().stream()
                        .map(DuplicateCandidateResponse::from)
                        .toList());
    }
}
