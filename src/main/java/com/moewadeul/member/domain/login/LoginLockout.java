package com.moewadeul.member.domain.login;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;

/**
 * 로그인 실패가 쌓였을 때 계정을 잠그는 규칙이다. 같은 이메일로 {@value #MAX_FAILURES}번 연속 실패하면
 * 그 실패 중 가장 오래된 것부터 {@link #WINDOW}가 지날 때까지 로그인을 막는다.
 */
public final class LoginLockout {

    public static final int MAX_FAILURES = 5;
    public static final Duration WINDOW = Duration.ofMinutes(10);

    private LoginLockout() {}

    /** 호출하면 실패를 셀 구간의 시작 시각을 돌려준다. 이 시각과 같거나 더 이른 실패는 세지 않는다. */
    public static Instant windowStart(Instant now) {
        return now.minus(WINDOW);
    }

    /**
     * 호출하면 이메일 하나의 로그인 시도를 한 줄로 세우는 잠금 행의 키를 돌려준다. 원문 대신 SHA-256 소문자 16진수 64자를 쓴다.
     * 가입하지 않은 이메일도 같은 방식으로 잠그므로, 응답 차이로 가입 여부가 드러나지 않는다.
     */
    public static String attemptKey(String attemptedEmail) {
        try {
            byte[] digest =
                    MessageDigest.getInstance("SHA-256").digest(attemptedEmail.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256을 쓸 수 없습니다.", e);
        }
    }

    /**
     * 호출하면 잠금이 풀리는 시각을 돌려준다. 잠기지 않았으면 빈 값이다.
     *
     * <p>잠금이 풀리는 시각은 구간 안의 가장 최근 실패 {@value #MAX_FAILURES}건 중 가장 오래된 것에 {@link #WINDOW}를 더한 값이다.
     * 그 실패가 구간을 벗어나는 순간에 구간 안 실패가 {@value #MAX_FAILURES}건 아래로 내려가기 때문이다.
     *
     * @param failureTimesSinceLastSuccess 마지막 성공 이후의 실패 시각. 성공 이전의 실패는 넘기지 않는다.
     */
    public static Optional<Instant> lockedUntil(List<Instant> failureTimesSinceLastSuccess, Instant now) {
        Instant windowStart = windowStart(now);
        List<Instant> inWindow = failureTimesSinceLastSuccess.stream()
                .filter(failedAt -> failedAt.isAfter(windowStart))
                .sorted()
                .toList();
        if (inWindow.size() < MAX_FAILURES) {
            return Optional.empty();
        }
        Instant oldestOfLatestFailures = inWindow.get(inWindow.size() - MAX_FAILURES);
        return Optional.of(oldestOfLatestFailures.plus(WINDOW));
    }
}
