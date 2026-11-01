package com.moewadeul.basecamp.infra.query;

import com.moewadeul.basecamp.domain.BasecampApplicationStatus;
import com.moewadeul.basecamp.domain.BasecampMemberStatus;

public record ViewerHistoryRow(
        long basecampId, BasecampMemberStatus memberStatus, BasecampApplicationStatus applicationStatus) {}
