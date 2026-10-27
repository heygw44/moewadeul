package com.moewadeul.publicdata.infra.forest;

public record ForestStandardRecord(
        String name,
        String province,
        String lodgingAvailable,
        String address,
        String phone,
        String homepage,
        String latitude,
        String longitude,
        String sourceDate) {}
