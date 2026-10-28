package com.moewadeul.community.application.comment;

import java.util.List;

public record CommunityCommentPage(List<CommunityCommentItem> content, int page, int size, boolean hasNext) {}
