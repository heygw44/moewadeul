package com.moewadeul.basecamp.application.membership;

import com.moewadeul.basecamp.domain.BasecampApplicationStatus;

public record BasecampApplyResult(long applicationId, BasecampApplicationStatus status) {}
