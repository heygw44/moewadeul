package com.moewadeul.common.scheduling;

import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.stereotype.Component;

// Spring은 컨텍스트를 닫을 때 실행 중인 @Scheduled 작업을 기다리기 전에 ContextClosedEvent를 먼저 발행한다.
@Component
public class ShutdownSignal implements ApplicationListener<ContextClosedEvent> {

    private volatile boolean requested;

    public boolean isRequested() {
        return requested;
    }

    public void request() {
        requested = true;
    }

    @Override
    public void onApplicationEvent(ContextClosedEvent event) {
        request();
    }
}
