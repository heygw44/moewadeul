package com.moewadeul.memory.application;

/** 기록에 붙은 사진 한 장. url은 응답마다 새로 서명한 조회 URL이다. */
public record MemoryPhoto(long imageId, String url) {}
