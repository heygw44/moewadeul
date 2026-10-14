package com.moewadeul.memory.infra.image;

/** 사진 조회가 읽은 사진 한 장. memoryId는 붙은 기록이고, 정리 대상 조회에서는 null이다. 키는 서버 안에서만 쓰고 응답에 내보내지 않는다. */
public record MemoryImageRow(long imageId, Long memoryId, String objectKey) {}
