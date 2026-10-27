package com.moewadeul.admin.api.spot;

import com.moewadeul.spot.application.moderation.AdminSpotPage;
import java.util.List;

public record AdminSpotPageResponse(List<AdminSpotSummaryResponse> content, int page, int size, boolean hasNext) {

    static AdminSpotPageResponse from(AdminSpotPage page) {
        return new AdminSpotPageResponse(
                page.content().stream().map(AdminSpotSummaryResponse::from).toList(),
                page.page(),
                page.size(),
                page.hasNext());
    }
}
