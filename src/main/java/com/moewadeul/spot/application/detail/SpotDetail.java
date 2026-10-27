package com.moewadeul.spot.application.detail;

import com.moewadeul.spot.application.SpotParkWarning;
import com.moewadeul.spot.domain.SpotType;
import java.util.List;

public record SpotDetail(
        long spotId,
        SpotType type,
        String name,
        double lat,
        double lng,
        String address,
        SpotParkWarning parkWarning,
        SpotBakjiDetail bakji,
        SpotPublicDetail publicDetail,
        SpotRating rating,
        List<SpotRecentReview> recentReviews,
        List<SpotExpectedPeople> expectedPeople,
        List<SpotRecruitingBasecamp> recruitingBasecamps) {}
