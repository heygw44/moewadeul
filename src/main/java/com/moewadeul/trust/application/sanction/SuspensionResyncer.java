package com.moewadeul.trust.application.sanction;

import com.moewadeul.member.application.MemberSuspensionService;
import com.moewadeul.trust.domain.sanction.Sanction;
import com.moewadeul.trust.domain.sanction.SanctionRepository;
import com.moewadeul.trust.domain.sanction.SanctionType;
import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SuspensionResyncer {

    private final SanctionRepository sanctionRepository;
    private final MemberSuspensionService memberSuspensionService;
    private final Clock clock;

    public void resync(long memberId) {
        Instant now = clock.instant();
        List<Sanction> active = sanctionRepository.findActiveSuspensions(memberId);
        boolean permanent = active.stream().anyMatch(sanction -> sanction.getType() == SanctionType.PERMANENT);
        Instant latestEnd = active.stream()
                .map(Sanction::getEndsAt)
                .filter(Objects::nonNull)
                .filter(endsAt -> endsAt.isAfter(now))
                .max(Comparator.naturalOrder())
                .orElse(null);
        memberSuspensionService.resyncSuspension(memberId, permanent, latestEnd);
    }
}
