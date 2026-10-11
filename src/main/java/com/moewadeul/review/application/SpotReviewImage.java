package com.moewadeul.review.application;

/** 후기에 붙은 이미지 한 장. url은 서버가 발급한 사전 서명 조회 URL이다. */
public record SpotReviewImage(long imageId, String url) {}
