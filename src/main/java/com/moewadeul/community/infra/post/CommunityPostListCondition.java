package com.moewadeul.community.infra.post;

public record CommunityPostListCondition(
        Long spotId, Integer minLikeCount, String searchType, String keyword, Long minPostId) {}
