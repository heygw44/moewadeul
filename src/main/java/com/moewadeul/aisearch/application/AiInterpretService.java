package com.moewadeul.aisearch.application;

import com.moewadeul.common.error.BusinessException;
import com.moewadeul.common.error.RateLimitedException;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 회원의 문장을 장소 검색 조건으로 바꾸는 유스케이스.
 *
 * <p>이 클래스에는 트랜잭션을 걸지 않는다. OpenAI 호출이 느려지면 DB 커넥션을 붙잡기 때문이다. 사용량 기록은 {@link AiSearchUsageRecorder}의 짧은
 * 트랜잭션이 맡는다. 문장은 OpenAI에 보내는 데만 쓰고 로그, 예외 메시지, 지표 태그, DB에 남기지 않는다.
 */
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

    /**
     * 호출하면 오늘 한도를 하나 쓰고, 서비스 전체의 하루 비용 한도가 남았을 때만 OpenAI로 문장을 해석한다.
     *
     * @throws RateLimitedException 회원의 오늘 한도를 다 썼을 때
     * @throws BusinessException 하루 비용 한도에 닿았거나 OpenAI 호출·응답 검증이 실패했을 때(AI_SEARCH_UNAVAILABLE)
     */
    public SpotQueryConditions interpret(long memberId, String query) {
        LocalDate today = AiSearchDay.today(clock);
        claim(memberId, today);
        requireBudgetLeft(today);
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
            // 응답은 받았지만 내용 검증에서 실패한 호출도 OpenAI가 비용을 매기므로 토큰을 센다.
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

    // 원인 예외의 메시지에는 실패 종류만 있고 문장은 없다. 그래서 원인을 달아 두어도 로그에 문장이 새지 않는다.
    private static BusinessException unavailable(Throwable cause) {
        BusinessException exception = new BusinessException(AiSearchErrorCode.AI_SEARCH_UNAVAILABLE);
        if (cause != null) {
            exception.initCause(cause);
        }
        return exception;
    }
}
