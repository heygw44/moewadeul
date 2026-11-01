package com.moewadeul.basecamp.application.query;

import com.moewadeul.basecamp.domain.BasecampRelation;
import com.moewadeul.basecamp.domain.BasecampStatus;

public record MyBasecampListQuery(BasecampRelation relation, BasecampStatus status, int page, int size) {}
