package com.moewadeul.member.domain.login;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;

public final class LoginLockout {

    public static final int MAX_FAILURES = 5;
    public static final Duration WINDOW = Duration.ofMinutes(10);

    public static final int IP_FAILURE_CAPACITY = 20;
    public static final Duration IP_FAILURE_WINDOW = Duration.ofMinutes(1);

    private LoginLockout() {}

    public static Instant windowStart(Instant now) {
        return now.minus(WINDOW);
    }

    /**
     * DB는 전각 문자나 보이지 않는 문자가 섞인 표기도 같은 회원으로 찾는다. 표기로 잠그면 표기마다 다른 행을 잡아 서로 기다리지 않으므로,
     * 회원을 찾았으면 회원 id로 잠근다. 회원이 없는 이메일은 어떤 비밀번호도 통과하지 못해서 표기대로 잠가도 된다.
     */
    public static String attemptKey(Long memberId, String attemptedEmail) {
        String subject = memberId != null ? "member:" + memberId : "email:" + attemptedEmail;
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(subject.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256을 쓸 수 없습니다.", e);
        }
    }

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
