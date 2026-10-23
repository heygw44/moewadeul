package com.moewadeul.common.scheduling;

import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.stereotype.Component;

/**
 * 앱이 종료를 시작했는지 알려 주는 신호다. 한 번 켜지면 다시 꺼지지 않는다.
 *
 * <p>스프링은 앱을 닫을 때 실행 중인 {@code @Scheduled} 작업이 끝나기를 기다린다. 그런데 아웃박스 발행기와 알림 메일 발송 작업은 한 번에
 * 여러 건을 집어 차례로 처리한다. 묶음 전체가 끝나기를 기다리면 종료가 Docker의 종료 유예를 넘겨 강제 종료되고, 집어 둔 행은 임대 시간 동안
 * 아무도 집지 못한다. 그래서 이런 작업은 한 건을 마칠 때마다 이 신호를 확인하고, 켜져 있으면 처리하던 한 건만 마친 뒤 멈춘다.
 *
 * <p>스프링은 컨텍스트를 닫기 시작할 때 {@link ContextClosedEvent}를 가장 먼저 발행한다. 실행 중인 작업을 기다리는 종료 단계는 그 뒤에
 * 오므로, 작업은 기다림이 시작되기 전에 신호를 보고 스스로 멈출 수 있다.
 */
@Component
public class ShutdownSignal implements ApplicationListener<ContextClosedEvent> {

    private volatile boolean requested;

    /** 호출하면 앱이 종료를 시작했는지 돌려준다. */
    public boolean isRequested() {
        return requested;
    }

    /** 호출하면 신호를 켠다. 스프링이 컨텍스트를 닫을 때 부르고, 테스트가 종료 시점을 직접 정할 때도 쓴다. */
    public void request() {
        requested = true;
    }

    @Override
    public void onApplicationEvent(ContextClosedEvent event) {
        request();
    }
}
