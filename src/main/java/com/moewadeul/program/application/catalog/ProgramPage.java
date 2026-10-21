package com.moewadeul.program.application.catalog;

import java.time.Instant;
import java.util.List;

/**
 * 공개 행사 목록의 한 페이지다. 전체 개수는 세지 않고, 다음 페이지가 있는지만 hasNext로 알려 준다.
 *
 * @param content 행사 시작 시각이 이른 순서의 행사 목록
 * @param page 0부터 센 페이지 번호
 * @param size 요청한 페이지 크기
 * @param nextApplyOpenAt 취소되지 않은 행사 중 신청 오픈 시각이 지금보다 늦은 것 가운데 가장 이른 시각. 그런 행사가 없으면 null이다. 필터와 페이지에 상관없다.
 */
public record ProgramPage(List<ProgramSummary> content, int page, int size, boolean hasNext, Instant nextApplyOpenAt) {}
