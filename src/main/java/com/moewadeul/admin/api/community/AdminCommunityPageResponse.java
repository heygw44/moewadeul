package com.moewadeul.admin.api.community;

import com.moewadeul.community.application.moderation.AdminCommunityPage;
import java.util.List;
import java.util.function.Function;

public record AdminCommunityPageResponse<T>(List<T> content, int page, int size, boolean hasNext) {

    static <S, T> AdminCommunityPageResponse<T> from(AdminCommunityPage<S> page, Function<S, T> mapper) {
        return new AdminCommunityPageResponse<>(
                page.content().stream().map(mapper).toList(), page.page(), page.size(), page.hasNext());
    }
}
