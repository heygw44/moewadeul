package com.moewadeul.common.external;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * 외부 API(기상청, 천문연, OpenAI, S3)를 부르는 클라이언트가 함께 쓰는 설정.
 *
 * @param maxConcurrentCalls API 하나에 동시에 보낼 수 있는 호출 수. 외부 API가 느려지면 요청 스레드가 응답을 기다리며 묶이는데, 이 수를 넘는
 *     호출은 기다리지 않고 바로 실패로 끝낸다. 그래서 외부 API 하나가 느려도 묶이는 요청 스레드는 이 수를 넘지 않는다.
 */
@ConfigurationProperties("moewadeul.external-api")
public record ExternalApiProperties(@DefaultValue("10") int maxConcurrentCalls) {

    public ExternalApiProperties {
        if (maxConcurrentCalls < 1) {
            throw new IllegalArgumentException("moewadeul.external-api.maxConcurrentCalls must be positive");
        }
    }
}
