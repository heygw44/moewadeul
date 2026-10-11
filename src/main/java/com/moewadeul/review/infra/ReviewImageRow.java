package com.moewadeul.review.infra;

/** 이미지 조회가 읽은 이미지 한 장. reviewId는 붙은 후기이고, 정리 대상 조회에서는 null이다. 키는 서버 안에서만 쓰고 응답에 내보내지 않는다. */
public record ReviewImageRow(long imageId, Long reviewId, String objectKey) {}
