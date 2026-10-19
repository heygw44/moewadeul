package com.moewadeul.publicdata.application.rejudge;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * 박지 재판정 설정.
 *
 * @param staleAfter 실행 중인 기록이 이 시간보다 오래 갱신되지 않으면, 다음 실행은 서버가 작업 도중 꺼진 것으로 보고 그 기록을 실패로 처리한다.
 *     재판정은 묶음 하나를 커밋할 때마다 기록을 갱신하므로, 이 값은 묶음 하나를 처리하는 시간보다 충분히 길어야 한다.
 * @param chunkSize 재판정이 한 트랜잭션에서 읽고 판정하고 커밋하는 박지 수. 이 수만큼의 좌표와 판정 결과만 메모리에 들고, 이 수만큼의 박지 행만
 *     커밋까지 잠근다.
 */
@ConfigurationProperties("moewadeul.publicdata.bakji-rejudge")
public record BakjiRejudgeProperties(
        @DefaultValue("30m") Duration staleAfter,
        @DefaultValue("1000") int chunkSize) {

    public BakjiRejudgeProperties {
        if (staleAfter.isZero() || staleAfter.isNegative()) {
            throw new IllegalArgumentException("moewadeul.publicdata.bakji-rejudge.staleAfter must be positive");
        }
        if (chunkSize <= 0) {
            throw new IllegalArgumentException("moewadeul.publicdata.bakji-rejudge.chunkSize must be positive");
        }
    }
}
