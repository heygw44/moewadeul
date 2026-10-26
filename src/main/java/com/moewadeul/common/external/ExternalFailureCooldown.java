package com.moewadeul.common.external;

import java.time.Clock;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * 외부 API가 응답하지 못하는 동안 그 API를 부르지 않고 쉰다.
 *
 * <p>기상청과 천문연은 응답이 없거나 5xx를 주면 클라이언트가 한 번 다시 보낸다. 장애가 이어지는 동안 날씨 요청마다 이 호출이 되풀이되면, 요청은 제한
 * 시간(약 4초)을 기다리고 기상청을 요청마다 2번 부르며, 공공데이터포털은 재시도한 요청도 하루 호출 한도에서 뺀다. 그래서 이 클래스는 호출이 다시
 * 보내기까지 실패하면 정한 시간 동안 그 API를 부르지 않고 바로 실패로 끝낸다. 호출 하나가 우연히 한 번 실패한 경우는 클라이언트의 재시도가
 * 받아서 여기까지 오지 않는다.
 *
 * <p>쉬는 시간이 지나면 한 호출만 시험 삼아 보내고, 그 호출이 끝날 때까지 다른 호출은 계속 건너뛴다. 시험 호출이 성공하면 바로 정상으로
 * 돌아오고, 또 실패하면 다시 쉰다. 그래서 장애가 이어지는 동안 외부 API로 가는 호출은 쉬는 시간마다 한 건(재시도 포함 두 번)이다.
 *
 * <p>쉬는 상태는 이 서버의 메모리에만 있다. 서버가 여러 대면 서버마다 따로 쉰다.
 */
public final class ExternalFailureCooldown {

    private final String api;
    private final long cooldownMillis;
    private final Clock clock;
    private final ExternalApiMetrics metrics;
    // 0이면 쉬지 않는다. 그 밖의 값은 이 시각(epoch 밀리초)까지 호출하지 않는다.
    private final AtomicLong restUntil = new AtomicLong();

    /**
     * @param api 외부 API 이름. 지표의 태그로 쓴다. 예: {@code kma}
     * @param properties 쉬는 시간 설정
     * @param clock 쉬는 시간을 재는 시계
     * @param metrics 건너뛴 호출 수를 남길 지표
     */
    public ExternalFailureCooldown(
            String api, ExternalApiProperties properties, Clock clock, ExternalApiMetrics metrics) {
        this.api = api;
        this.cooldownMillis = properties.failureCooldown().toMillis();
        this.clock = clock;
        this.metrics = metrics;
    }

    /**
     * 쉬는 중이 아니면 {@code call}을 실행하고 결과로 상태를 바꾼다. 쉬는 중이면 {@code call}을 실행하지 않고 {@code skipped}가 만든 예외를 던진다.
     *
     * @param call 외부 API를 부르는 작업(재시도 포함)
     * @param isOutage {@code call}이 던진 예외가 "다시 보내도 응답하지 못하는 상태"인지 판단한다. 참이면 쉬기 시작하고, 거짓이면(원천이 오류 코드로
     *     답했다면 API는 살아 있다) 정상으로 돌아온다.
     * @param skipped 쉬는 중일 때 던질 예외를 만드는 함수. 호출한 쪽이 외부 API 실패로 처리하는 예외를 만들어야 한다.
     */
    public <T> T call(
            Supplier<T> call, Predicate<RuntimeException> isOutage, Supplier<? extends RuntimeException> skipped) {
        if (cooldownMillis == 0) {
            return call.get();
        }
        if (!tryStart()) {
            metrics.recordSkipped(api);
            throw skipped.get();
        }
        try {
            T result = call.get();
            restUntil.set(0);
            return result;
        } catch (RuntimeException e) {
            restUntil.set(isOutage.test(e) ? clock.millis() + cooldownMillis : 0);
            throw e;
        }
    }

    // 쉬는 시간이 끝났으면 한 호출만 이어서 보낼 수 있다. 그 호출이 끝날 때까지 다른 호출이 보지 못하게 쉬는 시간을 먼저 늘려 둔다.
    private boolean tryStart() {
        long until = restUntil.get();
        if (until == 0) {
            return true;
        }
        long now = clock.millis();
        if (now < until) {
            return false;
        }
        return restUntil.compareAndSet(until, now + cooldownMillis);
    }
}
