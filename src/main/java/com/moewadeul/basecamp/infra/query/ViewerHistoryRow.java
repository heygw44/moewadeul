package com.moewadeul.basecamp.infra.query;

import com.moewadeul.basecamp.domain.BasecampApplicationStatus;
import com.moewadeul.basecamp.domain.BasecampMemberStatus;

/**
 * 한 회원이 한 베이스캠프에 남긴 멤버 행과 신청 행의 상태다. 둘 중 없는 쪽은 null이다.
 */
public record ViewerHistoryRow(
        long basecampId, BasecampMemberStatus memberStatus, BasecampApplicationStatus applicationStatus) {}
