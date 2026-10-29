package com.moewadeul.member.application;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;

/**
 * 아웃박스는 메일이 실제로 나갔어도 전송 시간 초과나 발행 기록 전 종료가 있으면 같은 이벤트를 다시 처리한다.
 * 그때 코드나 토큰을 새로 뽑으면 먼저 도착한 메일의 값이 쓸모없어진다. 그래서 (용도, 이벤트, 회원)의 HMAC으로 만들어
 * 다시 처리해도 같은 값을 보낸다. DB에는 지금처럼 그 값의 해시만 둔다. {@link Mac}은 스레드에 안전하지 않아서 부를 때마다 새로 만든다.
 */
@Component
public class MailSecretDeriver {

    private static final String ALGORITHM = "HmacSHA256";

    private final SecretKeySpec key;

    MailSecretDeriver(MailSecretProperties properties) {
        this.key = new SecretKeySpec(properties.hmacKey().getBytes(StandardCharsets.UTF_8), ALGORITHM);
    }

    public byte[] derive(Purpose purpose, long outboxEventId, long memberId) {
        String input = purpose.name() + ":" + outboxEventId + ":" + memberId;
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(key);
            return mac.doFinal(input.getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("메일에 담을 비밀값을 만들 수 없습니다.", e);
        }
    }

    public enum Purpose {
        EMAIL_VERIFICATION,
        PASSWORD_RESET
    }
}
