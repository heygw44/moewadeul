package com.moewadeul.spot.application.moderation;

import java.util.List;

public record AdminSpotPage(List<AdminSpotSummary> content, int page, int size, boolean hasNext) {}
