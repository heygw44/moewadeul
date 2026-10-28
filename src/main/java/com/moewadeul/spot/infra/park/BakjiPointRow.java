package com.moewadeul.spot.infra.park;

public record BakjiPointRow(long id, double lat, double lng, boolean parkWarning, long version) {}
