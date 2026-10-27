package com.moewadeul.review.application;

import java.time.Instant;
import java.util.Map;

public record ReviewImageUpload(long imageId, String uploadUrl, Map<String, String> headers, Instant expiresAt) {}
