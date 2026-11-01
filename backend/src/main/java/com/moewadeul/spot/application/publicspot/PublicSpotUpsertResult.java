package com.moewadeul.spot.application.publicspot;

public record PublicSpotUpsertResult(int inserted, int updated, int unchanged, int skipped) {}
