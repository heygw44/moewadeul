package com.moewadeul.basecamp.application.lifecycle;

import com.moewadeul.basecamp.domain.Basecamp;
import com.moewadeul.basecamp.infra.lifecycle.BasecampContactCleanupMapper;
import com.moewadeul.common.transaction.OutsideTransaction;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class BasecampContactCleanupService {

    private static final int BATCH_SIZE = 500;

    private final BasecampContactCleanupMapper contactCleanupMapper;
    private final Clock clock;

    public int clearExpiredContacts() {
        OutsideTransaction.require("묶음마다 따로 커밋해야 정리하는 동안 다른 베이스캠프 쓰기를 막지 않는다.");
        Instant now = clock.instant();
        Instant completedBefore = now.minus(Basecamp.CONTACT_VISIBLE_AFTER_COMPLETION);
        int cleared = 0;
        List<Long> ids;
        do {
            ids = contactCleanupMapper.selectClearableIds(completedBefore, BATCH_SIZE);
            if (!ids.isEmpty()) {
                cleared += contactCleanupMapper.clearContactInfo(ids, completedBefore, now);
            }
        } while (ids.size() >= BATCH_SIZE);
        log.info("basecamp contact cleared count={}", cleared);
        return cleared;
    }
}
