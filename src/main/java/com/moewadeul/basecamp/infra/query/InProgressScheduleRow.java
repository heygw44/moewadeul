package com.moewadeul.basecamp.infra.query;

import java.time.LocalDate;

/** 한 회원이 ACTIVE 멤버로 들어 있고 아직 끝나지 않은(모집 중·마감·확정) 베이스캠프의 일정이다. */
public record InProgressScheduleRow(long basecampId, LocalDate startDate, LocalDate endDate) {}
