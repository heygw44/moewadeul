package com.moewadeul.notification.application.event;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * 알림 메일 발송 작업 설정.
 *
 * @param senderEnabled false이면 주기적으로 메일을 보내는 스케줄러를 만들지 않는다. 테스트가 발송 시점을 직접 정할 때 쓴다.
 * @param pollDelay 한 번의 발송이 끝난 뒤 다음 발송을 시작하기까지 기다리는 시간
 * @param batchSize 한 번에 집는 알림 수의 상한
 * @param lease 발송 작업이 알림을 집은 뒤 다른 발송 작업이 같은 알림을 집지 못하게 막는 시간. 집은 알림을 다 보내는 시간보다 길어야 한다.
 *     한 통의 SMTP 제한 시간(연결·읽기·쓰기 각 5초)과 batchSize를 곱한 값보다 넉넉하게 둔다.
 * @param retryDelay 첫 실패 뒤 다시 집기까지 기다리는 시간. 실패할 때마다 두 배로 늘린다. 짧게 고정하면 메일 서버가 몇 분 멈추는 동안
 *     재시도 횟수가 모두 소진된다.
 * @param retryMaxDelay 늘어나는 재시도 간격의 상한. 메일 서버가 오래 멈춰도 돌아온 뒤 이 시간 안에 다시 시도한다.
 * @param maxAttempts 이 횟수만큼 실패하면 FAILED로 두고 다시 시도하지 않는다. 다만 메일 서버에 연결하지 못했거나 4xx 응답을 받은
 *     일시적인 실패는 횟수와 상관없이 계속 다시 시도한다.
 */
@ConfigurationProperties("moewadeul.notification.mail")
public record NotificationMailProperties(
        @DefaultValue("true") boolean senderEnabled,

        @DefaultValue(NotificationMailProperties.DEFAULT_POLL_DELAY)
        Duration pollDelay,

        @DefaultValue("20") int batchSize,
        @DefaultValue("5m") Duration lease,
        @DefaultValue("30s") Duration retryDelay,
        @DefaultValue("10m") Duration retryMaxDelay,
        @DefaultValue("10") int maxAttempts) {

    /** 스케줄러 어노테이션의 속성 기본값과 같은 값을 쓰려고 상수로 둔다. */
    static final String DEFAULT_POLL_DELAY = "1s";

    public NotificationMailProperties {
        requirePositive(batchSize, "batchSize");
        requirePositive(maxAttempts, "maxAttempts");
        requirePositive(pollDelay, "pollDelay");
        requirePositive(lease, "lease");
        requirePositive(retryDelay, "retryDelay");
        requirePositive(retryMaxDelay, "retryMaxDelay");
        if (retryMaxDelay.compareTo(retryDelay) < 0) {
            throw new IllegalArgumentException(
                    "moewadeul.notification.mail.retryMaxDelay must not be shorter than retryDelay");
        }
    }

    private static void requirePositive(int value, String name) {
        if (value <= 0) {
            throw new IllegalArgumentException("moewadeul.notification.mail." + name + " must be positive");
        }
    }

    private static void requirePositive(Duration value, String name) {
        if (value.isZero() || value.isNegative()) {
            throw new IllegalArgumentException("moewadeul.notification.mail." + name + " must be positive");
        }
    }
}
