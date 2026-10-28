package com.moewadeul.common.persistence;

import java.time.Instant;

public record DomainProbeView(String domain, String source, Instant createdAt) {}
