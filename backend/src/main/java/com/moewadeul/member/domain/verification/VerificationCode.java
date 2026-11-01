package com.moewadeul.member.domain.verification;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

public record VerificationCode(String value) {

    public static final int LENGTH = 6;

    private static final int BOUND = 1_000_000;
    private static final String HASH_ALGORITHM = "SHA-256";

    public VerificationCode {
        if (!isWellFormed(value)) {
            throw new IllegalArgumentException("인증 코드는 숫자 " + LENGTH + "자리여야 합니다.");
        }
    }

    public static VerificationCode fromSeed(byte[] seed) {
        String digits =
                Long.toString(Long.remainderUnsigned(ByteBuffer.wrap(seed).getLong(), BOUND));
        return new VerificationCode("0".repeat(LENGTH - digits.length()) + digits);
    }

    public static boolean isWellFormed(String code) {
        if (code == null || code.length() != LENGTH) {
            return false;
        }
        return code.chars().allMatch(c -> c >= '0' && c <= '9');
    }

    public static String hash(long memberId, String code) {
        MessageDigest digest = newDigest();
        byte[] hashed = digest.digest((memberId + ":" + code).getBytes(StandardCharsets.UTF_8));
        return HexFormat.of().formatHex(hashed);
    }

    public static boolean matches(long memberId, String code, String storedHash) {
        if (code == null || storedHash == null) {
            return false;
        }
        byte[] expected = storedHash.getBytes(StandardCharsets.UTF_8);
        byte[] actual = hash(memberId, code).getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(expected, actual);
    }

    private static MessageDigest newDigest() {
        try {
            return MessageDigest.getInstance(HASH_ALGORITHM);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(HASH_ALGORITHM + " 알고리즘을 쓸 수 없습니다.", e);
        }
    }

    @Override
    public String toString() {
        return "VerificationCode[value=****]";
    }
}
