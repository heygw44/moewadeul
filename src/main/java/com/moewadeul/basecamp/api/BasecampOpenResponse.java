package com.moewadeul.basecamp.api;

import com.moewadeul.basecamp.application.BasecampOpenResult;

public record BasecampOpenResponse(long basecampId, String status) {

    static BasecampOpenResponse from(BasecampOpenResult result) {
        return new BasecampOpenResponse(result.basecampId(), result.status());
    }
}
