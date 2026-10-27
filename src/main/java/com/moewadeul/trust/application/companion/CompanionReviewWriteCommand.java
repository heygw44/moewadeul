package com.moewadeul.trust.application.companion;

import com.moewadeul.trust.domain.companion.CompanionReviewTag;
import java.util.List;

public record CompanionReviewWriteCommand(
        long revieweeId, boolean rejoinWanted, List<CompanionReviewTag> tags, String comment) {}
