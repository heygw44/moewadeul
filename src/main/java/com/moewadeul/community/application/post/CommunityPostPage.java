package com.moewadeul.community.application.post;

import java.util.List;

public record CommunityPostPage(
        List<CommunityPostItem> content, int page, int size, boolean hasNext, long totalElements, int totalPages) {

    public static CommunityPostPage of(List<CommunityPostItem> content, int page, int size, long totalElements) {
        int totalPages = (int) ((totalElements + size - 1) / size);
        return new CommunityPostPage(content, page, size, page + 1 < totalPages, totalElements, totalPages);
    }
}
