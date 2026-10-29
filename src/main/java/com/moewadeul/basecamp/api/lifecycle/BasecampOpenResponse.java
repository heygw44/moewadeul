package com.moewadeul.basecamp.api.lifecycle;

import com.moewadeul.basecamp.application.lifecycle.BasecampOpenResult;
import com.moewadeul.basecamp.domain.BasecampStatus;

public record BasecampOpenResponse(long basecampId, BasecampStatus status) {

    static BasecampOpenResponse from(BasecampOpenResult result) {
        return new BasecampOpenResponse(result.basecampId(), result.status());
    }
}
