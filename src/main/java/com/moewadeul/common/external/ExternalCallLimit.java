package com.moewadeul.common.external;

import java.util.concurrent.Semaphore;
import java.util.function.Supplier;

/**
 * 외부 API 하나에 동시에 보내는 호출 수를 제한한다.
 *
 * <p>외부 API 호출은 요청 스레드에서 응답을 기다린다. 그래서 외부 API가 느려진 채 호출이 계속 들어오면, 응답을 기다리는 스레드가 늘어나
 * Tomcat 스레드를 모두 차지하고, 외부 API와 상관없는 요청까지 대기열에서 기다린다. 이 클래스는 정한 수만큼만 동시에 호출하게 하고, 그 수를
 * 넘는 호출은 기다리게 하지 않고 바로 거부한다. 거부된 호출은 외부 API가 실패했을 때와 같은 경로로 처리된다(날씨 없이 응답, 503 등).
 *
 * <p>클라이언트는 API마다 인스턴스를 하나씩 둔다. 재시도도 같은 자리 안에서 하므로, 한 호출이 재시도까지 마칠 때까지 자리를 잡고 있다.
 */
public final class ExternalCallLimit {

    private final String api;
    private final Semaphore permits;
    private final ExternalApiMetrics metrics;

    /**
     * @param api 외부 API 이름. 지표의 태그로 쓴다. 예: {@code kma}
     * @param properties 동시 호출 수 설정
     * @param metrics 거부한 호출 수를 남길 지표
     */
    public ExternalCallLimit(String api, ExternalApiProperties properties, ExternalApiMetrics metrics) {
        this.api = api;
        this.permits = new Semaphore(properties.maxConcurrentCalls());
        this.metrics = metrics;
    }

    /**
     * 자리가 있으면 {@code call}을 실행하고 그 결과를 돌려준다. 자리가 없으면 기다리지 않고 {@code rejection}이 만든 예외를 던진다.
     *
     * @param call 외부 API를 부르는 작업(재시도 포함)
     * @param rejection 자리가 없을 때 던질 예외를 만드는 함수. 호출한 쪽이 외부 API 실패로 처리하는 예외를 만들어야 한다.
     */
    public <T> T call(Supplier<T> call, Supplier<? extends RuntimeException> rejection) {
        if (!permits.tryAcquire()) {
            metrics.recordRejected(api);
            throw rejection.get();
        }
        try {
            return call.get();
        } finally {
            permits.release();
        }
    }
}
