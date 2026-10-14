package com.moewadeul.aisearch.application;

/**
 * 하루 동안 모든 회원이 쓴 토큰의 합계. 회원 수가 늘면 INT 범위를 넘을 수 있어 long으로 받는다.
 *
 * @param inputTokens 입력 토큰 합계
 * @param outputTokens 출력 토큰 합계
 */
public record TokenTotals(long inputTokens, long outputTokens) {}
