package com.moewadeul.basecamp.application.membership;

import com.moewadeul.basecamp.domain.BasecampApplicationStatus;

public record BasecampRejectResult(long applicationId, BasecampApplicationStatus status) {}
