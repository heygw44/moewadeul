package com.moewadeul.spot.application.map;

import java.util.List;

public record SpotNearbyPage(List<SpotNearbyItem> content, int page, int size, boolean hasNext) {}
