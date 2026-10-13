package com.moewadeul.aisearch.application;

/**
 * 문장 해석 결과.
 *
 * @param conditions 읽어 낸 검색 조건
 * @param usage 이 해석에 쓴 토큰 수
 */
public record SpotQueryInterpretation(SpotQueryConditions conditions, TokenUsage usage) {}
