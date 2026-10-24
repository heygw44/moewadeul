package com.moewadeul.spot.infra.detail;

/**
 * 장소 한 곳의 후기 수와 평균 평점. 후기가 없으면 count는 0이고 average는 {@code null}이다. average는 회원별 평균 평점을 다시 평균 내어
 * 소수 첫째 자리까지 반올림한 값이고, count는 후기 개수다.
 */
public record SpotRatingRow(long count, Double average) {}
