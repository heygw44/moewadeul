package com.moewadeul.spot.infra.park;

/** 재판정 결과를 기록할 박지 ID와, 판정할 때 읽은 낙관적 잠금 번호. 기록할 때 번호가 그대로인 행만 바꾼다. */
public record BakjiJudgeTarget(long id, long version) {}
