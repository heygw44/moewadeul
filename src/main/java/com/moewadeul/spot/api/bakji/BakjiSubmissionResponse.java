package com.moewadeul.spot.api.bakji;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.moewadeul.spot.api.ParkWarningResponse;
import com.moewadeul.spot.application.bakji.BakjiSubmission;
import java.util.List;

/**
 * 박지를 제보하거나 수정한 결과. 공원 경계 경고, 안내 문구, 50m 안의 기존 박지(중복 후보)를 함께 준다. notice(관할 기관 확인 안내)는 경고가 아닌 박지에만
 * 내보낸다.
 */
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
