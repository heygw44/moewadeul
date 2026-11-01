package com.moewadeul.basecamp.application.membership;

import com.moewadeul.basecamp.domain.BasecampStatus;

public record BasecampApproveResult(int headcount, BasecampStatus status) {}
