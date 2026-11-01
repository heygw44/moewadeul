package com.moewadeul.community.application.comment;

public record CommunityCommentWriteCommand(String content, Long parentId) {}
