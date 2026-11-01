package com.moewadeul.aisearch.application;

import com.moewadeul.aisearch.infra.AiSearchUsageMapper;
import com.moewadeul.common.error.RateLimitedException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AiSearchUsageRecorder {

    static final int MEMBER_DAILY_LIMIT = 20;

    private final AiSearchUsageMapper usageMapper;
    private final Clock clock;

    @Transactional
    public void claim(long memberId, LocalDate date) {
        Instant now = Instant.now(clock);
        usageMapper.insertIfAbsent(memberId, date, now);
        int used = usageMapper.selectRequestCountForUpdate(memberId, date);
        if (used >= MEMBER_DAILY_LIMIT) {
            throw new RateLimitedException(AiSearchErrorCode.AI_SEARCH_LIMITED, AiSearchDay.untilNextDay(now, date));
        }
        usageMapper.incrementRequestCount(memberId, date, now);
    }

    @Transactional
    public void addTokens(long memberId, LocalDate date, TokenUsage usage) {
        usageMapper.addTokens(memberId, date, usage.inputTokens(), usage.outputTokens(), Instant.now(clock));
    }

    @Transactional(readOnly = true)
    public TokenTotals sumTokens(LocalDate date) {
        return usageMapper.sumTokensOn(date);
    }
}
