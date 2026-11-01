package com.moewadeul.basecamp.infra.query;

import com.moewadeul.basecamp.application.query.BasecampSpotType;

public record SpotSummaryRow(long spotId, String name, BasecampSpotType type, double lat, double lng) {}
