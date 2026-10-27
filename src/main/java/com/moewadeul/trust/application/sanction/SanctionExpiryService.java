package com.moewadeul.trust.application.sanction;

import com.moewadeul.member.application.MemberSuspensionService;
import com.moewadeul.trust.infra.sanction.SanctionExpiryMapper;
import java.time.Clock;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class SanctionExpiryService {

    private final SanctionExpiryMapper sanctionExpiryMapper;
    private final MemberSuspensionService memberSuspensionService;
    private final Clock clock;

    public Result run() {
        Instant now = clock.instant();
        int expiredSanctions = sanctionExpiryMapper.expireEnded(now);
        int releasedMembers = memberSuspensionService.releaseExpired(now);
        log.info("sanction expiry expiredSanctions={} releasedMembers={}", expiredSanctions, releasedMembers);
        return new Result(expiredSanctions, releasedMembers);
    }

    public record Result(int expiredSanctions, int releasedMembers) {}
}
