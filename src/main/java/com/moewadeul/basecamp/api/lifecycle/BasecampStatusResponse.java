package com.moewadeul.basecamp.api.lifecycle;

import com.moewadeul.basecamp.application.lifecycle.BasecampStatusResult;

public record BasecampStatusResponse(long basecampId, String status) {

    static BasecampStatusResponse from(BasecampStatusResult result) {
        return new BasecampStatusResponse(result.basecampId(), result.status());
    }
}
