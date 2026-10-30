package com.moewadeul.member.application.verification;

import com.moewadeul.member.application.MemberSessionCleanup;
import com.moewadeul.member.infra.verification.UnverifiedMemberMapper;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
class UnverifiedMemberDeleter {

    private final UnverifiedMemberMapper unverifiedMemberMapper;
    private final MemberSessionCleanup memberSessionCleanup;

    @Transactional
    int delete(List<Long> ids, Instant cutoff) {
        int deleted = unverifiedMemberMapper.deleteExpiredByIds(ids, cutoff);
        ids.forEach(memberSessionCleanup::recordInTransaction);
        return deleted;
    }
}
