package com.moewadeul.basecamp.application.lifecycle;

import com.moewadeul.basecamp.domain.BasecampStatus;

public record BasecampOpenResult(long basecampId, BasecampStatus status) {}
