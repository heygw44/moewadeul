package com.moewadeul.aisearch.application;

import com.moewadeul.aisearch.infra.AiSearchUsageMapper;
import com.moewadeul.common.error.RateLimitedException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * AI 검색 사용량(요청 횟수와 토큰 수)을 DB에 기록한다. 각 메서드는 짧은 트랜잭션 하나이고, OpenAI 호출은 이 안에서 하지 않는다.
 * 문장은 받지도 저장하지도 않는다.
 */
@Service
@RequiredArgsConstructor
public class AiSearchUsageRecorder {

    // 회원 한 명이 하루(한국 날짜)에 보낼 수 있는 AI 검색 요청 수다. 요청이 OpenAI 호출 비용으로 이어져서 사람마다 상한을 둔다.
    static final int MEMBER_DAILY_LIMIT = 20;

    private final AiSearchUsageMapper usageMapper;
    private final Clock clock;

    /**
     * 호출하면 그 회원의 오늘 요청 횟수를 하나 늘린다. 이미 한도만큼 썼으면 늘리지 않고 던진다.
     *
     * @throws RateLimitedException 오늘 한도를 다 썼을 때. 다음 날 0시(한국 시간)까지 남은 시간을 담는다.
     */
    @Transactional
    public void claim(long memberId, LocalDate date) {
        Instant now = Instant.now(clock);
        // 행을 먼저 만들고 잠가야 같은 회원의 동시 요청이 한 줄로 서서, 횟수를 읽고 올리는 사이에 다른 요청이 끼어들지 못한다.
        usageMapper.insertIfAbsent(memberId, date, now);
        int used = usageMapper.selectRequestCountForUpdate(memberId, date);
        if (used >= MEMBER_DAILY_LIMIT) {
            throw new RateLimitedException(AiSearchErrorCode.AI_SEARCH_LIMITED, AiSearchDay.untilNextDay(now, date));
        }
        usageMapper.incrementRequestCount(memberId, date, now);
    }

    /** 호출하면 OpenAI 호출 한 번에 쓴 토큰을 그 회원의 오늘 행에 더한다. */
    @Transactional
    public void addTokens(long memberId, LocalDate date, TokenUsage usage) {
        usageMapper.addTokens(memberId, date, usage.inputTokens(), usage.outputTokens(), Instant.now(clock));
    }

    /** 호출하면 그 날짜에 모든 회원이 쓴 토큰의 합계를 돌려준다. */
    @Transactional(readOnly = true)
    public TokenTotals sumTokens(LocalDate date) {
        return usageMapper.sumTokensOn(date);
    }
}
