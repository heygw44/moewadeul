package com.moewadeul.basecamp.infra.query;

public record SpotSummaryRow(long spotId, String name, String type, double lat, double lng) {}
