package com.moewadeul.community.application.post;

/** 글 상세에 담는 이미지 한 장. url은 조회용 사전 서명 URL이다. */
public record CommunityPostImage(long imageId, String url) {}
