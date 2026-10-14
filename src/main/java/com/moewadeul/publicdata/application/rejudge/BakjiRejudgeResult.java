package com.moewadeul.publicdata.application.rejudge;

/**
 * 박지 재판정 한 번의 결과.
 *
 * @param judgedCount 공원 경고를 다시 판정한 박지 수
 * @param newlyWarnedCount 경고가 없다가 새로 경고를 받은 박지 수
 * @param clearedCount 경고를 받았다가 경고가 풀린 박지 수
 */
public record BakjiRejudgeResult(int judgedCount, int newlyWarnedCount, int clearedCount) {}
