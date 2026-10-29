package com.moewadeul.aisearch.application;

import com.moewadeul.common.error.BusinessException;
import com.moewadeul.common.error.RateLimitedException;
import com.moewadeul.common.transaction.OutsideTransaction;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AiInterpretService {

    private static final String REQUESTS_METRIC = "moewadeul.aisearch.requests";
    private static final String UNPARSED_METRIC = "moewadeul.aisearch.unparsed";

    private final SpotQueryInterpreter interpreter;
    private final AiSearchUsageRecorder usageRecorder;
    private final AiSearchProperties properties;
    private final MeterRegistry meterRegistry;
    private final Clock clock;

    public SpotQueryConditions interpret(long memberId, String query) {
        OutsideTransaction.require("OpenAI 응답을 기다리는 동안 DB 커넥션을 쥐면 안 된다.");
        LocalDate today = AiSearchDay.today(clock);
        requireBudgetLeft(today);
        claim(memberId, today);
        SpotQueryInterpretation result = callInterpreter(memberId, today, query.strip());
        countRequest("success");
        if (!result.conditions().unparsed().isEmpty()) {
            meterRegistry.counter(UNPARSED_METRIC).increment();
        }
        return result.conditions();
    }

    private void claim(long memberId, LocalDate today) {
        try {
            usageRecorder.claim(memberId, today);
        } catch (RateLimitedException e) {
            countRequest("limited");
            throw e;
        }
    }

    private void requireBudgetLeft(LocalDate today) {
        if (properties.isBudgetExhausted(usageRecorder.sumTokens(today))) {
            countRequest("budget_exceeded");
            throw unavailable(null);
        }
    }

    private SpotQueryInterpretation callInterpreter(long memberId, LocalDate today, String query) {
        SpotQueryInterpretation result;
        try {
            result = interpreter.interpret(query);
        } catch (SpotQueryInterpretationException e) {
            e.usage().ifPresent(usage -> usageRecorder.addTokens(memberId, today, usage));
            countRequest("failed");
            throw unavailable(e);
        }
        usageRecorder.addTokens(memberId, today, result.usage());
        return result;
    }

    private void countRequest(String outcome) {
        meterRegistry.counter(REQUESTS_METRIC, "outcome", outcome).increment();
    }

    private static BusinessException unavailable(Throwable cause) {
        BusinessException exception = new BusinessException(AiSearchErrorCode.AI_SEARCH_UNAVAILABLE);
        if (cause != null) {
            exception.initCause(cause);
        }
        return exception;
    }
}
