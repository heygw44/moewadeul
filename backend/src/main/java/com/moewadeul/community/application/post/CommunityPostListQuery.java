package com.moewadeul.community.application.post;

public record CommunityPostListQuery(Long spotId, boolean popular, CommunitySearchType searchType, String keyword) {

    public CommunityPostListQuery {
        if (keyword == null) {
            searchType = null;
        } else if (searchType == null) {
            searchType = CommunitySearchType.TITLE_CONTENT;
        }
    }

    public CommunityPostListQuery(Long spotId, boolean popular) {
        this(spotId, popular, null, null);
    }

    public static CommunityPostListQuery all() {
        return new CommunityPostListQuery(null, false);
    }
}
