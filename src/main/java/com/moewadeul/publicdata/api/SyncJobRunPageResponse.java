package com.moewadeul.publicdata.api;

import com.moewadeul.publicdata.application.SyncJobRunPage;
import java.util.List;

public record SyncJobRunPageResponse(List<SyncJobRunResponse> content, int page, int size, boolean hasNext) {

    static SyncJobRunPageResponse from(SyncJobRunPage page) {
        List<SyncJobRunResponse> content =
                page.content().stream().map(SyncJobRunResponse::from).toList();
        return new SyncJobRunPageResponse(content, page.page(), page.size(), page.hasNext());
    }
}
