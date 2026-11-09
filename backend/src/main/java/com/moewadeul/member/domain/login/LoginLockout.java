package com.moewadeul.member.domain.login;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

public final class LoginLockout {

    public static final int MAX_FAILURES = 5;
    public static final int ACCOUNT_MAX_FAILURES = 50;
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

    /**
     * 같은 IP의 실패가 {@link #MAX_FAILURES}번이면 그 IP만 막아서, 이메일만 아는 사람이 다른 IP에서 본인을 잠그지 못하게 한다.
     * 여러 IP에 나눠 추측하는 것은 IP와 상관없이 {@link #ACCOUNT_MAX_FAILURES}번을 넘으면 모든 IP에서 막는다.
     */
    public static Optional<Instant> lockedUntil(List<LoginFailure> failuresSinceLastSuccess, String ip, Instant now) {
        Instant windowStart = windowStart(now);
        List<LoginFailure> inWindow = failuresSinceLastSuccess.stream()
                .filter(failure -> failure.failedAt().isAfter(windowStart))
                .toList();
        Optional<Instant> ipLock = lockedUntil(
                inWindow.stream().filter(failure -> failure.ip().equals(ip)).toList(), MAX_FAILURES);
        Optional<Instant> accountLock = lockedUntil(inWindow, ACCOUNT_MAX_FAILURES + 1);
        return Stream.of(ipLock, accountLock).flatMap(Optional::stream).max(Comparator.naturalOrder());
    }

    private static Optional<Instant> lockedUntil(List<LoginFailure> inWindow, int failuresToLock) {
        if (inWindow.size() < failuresToLock) {
            return Optional.empty();
        }
        List<Instant> failedAts =
                inWindow.stream().map(LoginFailure::failedAt).sorted().toList();
        Instant oldestOfLatestFailures = failedAts.get(failedAts.size() - failuresToLock);
        return Optional.of(oldestOfLatestFailures.plus(WINDOW));
    }
}
