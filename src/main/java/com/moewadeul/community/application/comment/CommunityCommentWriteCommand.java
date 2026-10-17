package com.moewadeul.community.application.comment;

/** 댓글 작성 요청. parentId는 최상위 댓글이면 null이고, 답글이면 답할 댓글의 ID다. 답할 댓글은 최상위 댓글이나 답글이다. */
public record CommunityCommentWriteCommand(String content, Long parentId) {}
