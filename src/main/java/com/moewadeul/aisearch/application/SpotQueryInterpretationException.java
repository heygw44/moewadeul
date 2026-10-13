package com.moewadeul.aisearch.application;

import java.util.Optional;

/**
 * 문장 해석이 실패했을 때 던진다.
 *
 * <p>메시지에는 실패 종류(시간 초과, HTTP 상태, 형식 불일치 등)만 넣는다. 사용자 문장, 응답 본문, 키는 개인정보이거나 비밀값이라 메시지에도 로그에도
 * 남기지 않는다.
 */
public class SpotQueryInterpretationException extends RuntimeException {

    private final transient TokenUsage usage;

    public SpotQueryInterpretationException(String message) {
        this(message, null, null);
    }

    public SpotQueryInterpretationException(String message, Throwable cause) {
        this(message, cause, null);
    }

    public SpotQueryInterpretationException(String message, Throwable cause, TokenUsage usage) {
        super(message, cause);
        this.usage = usage;
    }

    /** 응답은 받았지만 내용 검증에서 실패했을 때 채운다. 이런 호출도 비용이 들므로 호출한 쪽이 하루 한도에 더한다. */
    public Optional<TokenUsage> usage() {
        return Optional.ofNullable(usage);
    }
}
