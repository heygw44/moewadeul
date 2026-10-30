package com.moewadeul.member.application.verification;

import com.moewadeul.member.application.MemberSessions;
import com.moewadeul.member.domain.verification.UnverifiedMemberPolicy;
import com.moewadeul.member.infra.verification.UnverifiedMemberMapper;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class UnverifiedMemberCleanupService {

    static final int BATCH_SIZE = 500;

    private final UnverifiedMemberMapper unverifiedMemberMapper;
    private final MemberSessions memberSessions;
    private final Clock clock;

    public int deleteExpiredUnverifiedMembers() {
        Instant cutoff = UnverifiedMemberPolicy.deletionCutoff(Instant.now(clock));
        int deletedTotal = 0;
        List<Long> expiredIds = unverifiedMemberMapper.selectExpiredIds(cutoff, BATCH_SIZE);
        while (!expiredIds.isEmpty()) {
            int deleted = unverifiedMemberMapper.deleteExpiredByIds(expiredIds, cutoff);
            deleteSessionsOf(expiredIds);
            deletedTotal += deleted;
            if (deleted == 0 || expiredIds.size() < BATCH_SIZE) {
                break;
            }
            expiredIds = unverifiedMemberMapper.selectExpiredIds(cutoff, BATCH_SIZE);
        }
        log.info("unverified members deleted count={}", deletedTotal);
        return deletedTotal;
    }

    private void deleteSessionsOf(List<Long> memberIds) {
        memberIds.forEach(memberSessions::invalidateAll);
    }
}
