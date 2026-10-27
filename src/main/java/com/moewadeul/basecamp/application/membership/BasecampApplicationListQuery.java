package com.moewadeul.basecamp.application.membership;

import com.moewadeul.basecamp.domain.BasecampApplicationStatus;

public record BasecampApplicationListQuery(BasecampApplicationStatus status, int page, int size) {}
