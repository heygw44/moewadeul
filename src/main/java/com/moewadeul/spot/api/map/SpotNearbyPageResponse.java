package com.moewadeul.spot.api.map;

import com.moewadeul.spot.application.map.SpotNearbyPage;
import java.util.List;

public record SpotNearbyPageResponse(List<SpotNearbyItemResponse> content, int page, int size, boolean hasNext) {

    static SpotNearbyPageResponse from(SpotNearbyPage page) {
        List<SpotNearbyItemResponse> content =
                page.content().stream().map(SpotNearbyItemResponse::from).toList();
        return new SpotNearbyPageResponse(content, page.page(), page.size(), page.hasNext());
    }
}
