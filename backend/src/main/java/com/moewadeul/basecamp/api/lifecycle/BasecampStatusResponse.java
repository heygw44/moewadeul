package com.moewadeul.basecamp.api.lifecycle;

import com.moewadeul.basecamp.application.lifecycle.BasecampStatusResult;
import com.moewadeul.basecamp.domain.BasecampStatus;

public record BasecampStatusResponse(long basecampId, BasecampStatus status) {

    static BasecampStatusResponse from(BasecampStatusResult result) {
        return new BasecampStatusResponse(result.basecampId(), result.status());
    }
}
