package com.moewadeul.aisearch.application;

/**
 * OpenAI 호출 한 번에 쓴 토큰 수. 호출한 쪽이 하루 비용 한도를 계산하는 데 쓴다.
 *
 * @param inputTokens 입력(프롬프트)에 쓴 토큰 수
 * @param outputTokens 출력(답변)에 쓴 토큰 수
 */
public record TokenUsage(int inputTokens, int outputTokens) {

    public TokenUsage {
        if (inputTokens < 0 || outputTokens < 0) {
            throw new IllegalArgumentException("token counts must not be negative");
        }
    }
}
