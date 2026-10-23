package com.moewadeul.community.infra.image;

/**
 * 이미지 조회가 읽은 이미지 한 장의 ID와 저장소 객체 키. thumbnailKey는 썸네일 없이 올린 이미지면 null이다. 키는 서버 안에서만 쓰고 응답에
 * 내보내지 않는다.
 */
public record CommunityImageRow(long imageId, String objectKey, String thumbnailKey) {}
