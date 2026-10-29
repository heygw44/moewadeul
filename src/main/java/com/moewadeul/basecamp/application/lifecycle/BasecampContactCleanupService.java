package com.moewadeul.basecamp.application.lifecycle;

import com.moewadeul.basecamp.domain.Basecamp;
import com.moewadeul.basecamp.infra.lifecycle.BasecampContactCleanupMapper;
import java.time.Clock;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class BasecampContactCleanupService {

    private final BasecampContactCleanupMapper contactCleanupMapper;
    private final Clock clock;

    public int clearExpiredContacts() {
        Instant now = clock.instant();
        int cleared = contactCleanupMapper.clearContactInfo(now.minus(Basecamp.CONTACT_VISIBLE_AFTER_COMPLETION), now);
        log.info("basecamp contact cleared count={}", cleared);
        return cleared;
    }
}
