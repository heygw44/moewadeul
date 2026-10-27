package com.moewadeul.trust.infra.profile;

public record TrustRecordRow(
        long completedCompanions,
        long receivedReviews,
        long rejoinWanted,
        long recentEarlyLeaves,
        boolean recentSanction) {}
