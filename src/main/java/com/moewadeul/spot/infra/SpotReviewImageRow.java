package com.moewadeul.spot.infra;

/** 최근 후기에 붙은 이미지 한 장. 키는 서버 안에서만 쓰고 응답에 내보내지 않는다. */
public record SpotReviewImageRow(long imageId, long reviewId, String objectKey) {}
