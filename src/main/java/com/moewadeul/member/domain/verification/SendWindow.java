package com.moewadeul.member.domain.verification;

import java.time.Instant;

/**
 * 어떤 기간 안에 인증 코드를 발송한 횟수와 가장 오래된 발송 시각.
 *
 * @param count 기간 안에 발송한 횟수
 * @param oldestSentAt 기간 안에서 가장 오래된 발송 시각. 발송이 없으면 null이다.
 */
public record SendWindow(long count, Instant oldestSentAt) {

    /** 호출하면 두 기간의 횟수를 더하고, 가장 오래된 발송 시각은 둘 중 이른 쪽을 돌려준다. */
    public SendWindow plus(SendWindow other) {
        Instant oldest = oldestSentAt;
        if (oldest == null || (other.oldestSentAt != null && other.oldestSentAt.isBefore(oldest))) {
            oldest = other.oldestSentAt;
        }
        return new SendWindow(count + other.count, oldest);
    }
}
