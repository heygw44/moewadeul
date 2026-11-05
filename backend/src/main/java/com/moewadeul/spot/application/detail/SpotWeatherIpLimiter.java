package com.moewadeul.spot.application.detail;

import com.moewadeul.common.error.CommonErrorCode;
import com.moewadeul.common.error.RateLimitedException;
import com.moewadeul.common.ratelimit.IpTokenBucket;
import java.time.Clock;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 날씨는 로그인 없이 묻고, 캐시에 없으면 기상청 호출 자리와 Tomcat 스레드를 쓴다. 한 IP가 이 자리를 다 쓰지 못하게 막는다.
 * 화면은 장소 하나에 최대 세 번 묻는다. 여러 IP로 나눈 요청은 막지 못한다.
 */
@Component
public class SpotWeatherIpLimiter {

    private final IpTokenBucket bucket;

    public SpotWeatherIpLimiter(@Value("${moewadeul.weather.ip-requests-per-minute:60}") int perMinute, Clock clock) {
        this.bucket = new IpTokenBucket(perMinute, Duration.ofMinutes(1), clock);
    }

    public void acquire(String ip) {
        bucket.tryTake(ip).ifPresent(retryAfter -> {
            throw new RateLimitedException(CommonErrorCode.TOO_MANY_REQUESTS, retryAfter);
        });
    }
}
