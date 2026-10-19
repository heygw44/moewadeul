package com.moewadeul.spot.application.park;

/**
 * 박지 재판정 묶음 하나의 결과.
 *
 * @param counts 이 묶음의 집계
 * @param lastId 이 묶음에서 판정한 마지막 박지 ID. 다음 묶음은 이 ID보다 큰 박지부터 읽는다. 남은 박지가 없었으면 null이다.
 */
public record BakjiRejudgeChunk(BakjiRejudgeCounts counts, Long lastId) {}
