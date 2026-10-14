package com.moewadeul.basecamp.api.lifecycle;

import com.moewadeul.basecamp.application.lifecycle.BasecampOpenResult;

public record BasecampOpenResponse(long basecampId, String status) {

    static BasecampOpenResponse from(BasecampOpenResult result) {
        return new BasecampOpenResponse(result.basecampId(), result.status());
    }
}
