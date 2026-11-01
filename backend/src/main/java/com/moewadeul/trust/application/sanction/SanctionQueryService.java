package com.moewadeul.trust.application.sanction;

import com.moewadeul.trust.domain.sanction.SanctionRepository;
import java.time.Clock;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SanctionQueryService {

    private final SanctionRepository sanctionRepository;
    private final Clock clock;

    @Transactional(readOnly = true)
    public boolean hasActiveSuspension(long memberId) {
        return sanctionRepository.existsActiveSuspension(memberId, clock.instant());
    }
}
