package com.moewadeul.spot.application.detail;

/**
 * 장소의 평점. average는 회원마다 그 장소에 준 평점을 먼저 평균 내고, 그 회원별 평균을 다시 평균 내어 소수 첫째 자리에서 반올림한 값이다.
 * 그래서 한 회원이 방문일마다 후기를 여러 개 써도 평균에는 한 표만 들어간다. 후기가 없으면 {@code null}이다. count는 회원 수가 아니라 후기 수다.
 */
public record SpotRating(Double average, long count) {}
