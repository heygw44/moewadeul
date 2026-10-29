package com.moewadeul.community.application.post;

import java.util.List;

public record CommunityPostPage(List<CommunityPostItem> content, int page, int size, boolean hasNext) {}
