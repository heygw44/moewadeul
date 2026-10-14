package com.moewadeul.spot.application.park;

/**
 * 박지 재판정 한 번의 집계. 경고를 유지하면서 근거 경계만 바뀐 박지는 newlyWarned와 cleared 어디에도 세지 않는다.
 *
 * @param judged 판정한 박지 수
 * @param newlyWarned 경고가 없다가 새로 경고를 받은 박지 수
 * @param cleared 경고를 받았다가 경고가 풀린 박지 수
 */
public record BakjiRejudgeCounts(int judged, int newlyWarned, int cleared) {}
