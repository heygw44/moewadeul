package com.moewadeul.community.application.moderation;

import java.util.List;

public record AdminCommunityPage<T>(List<T> content, int page, int size, boolean hasNext) {}
