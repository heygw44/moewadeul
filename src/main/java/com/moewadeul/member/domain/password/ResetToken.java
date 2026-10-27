package com.moewadeul.member.domain.password;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import java.util.regex.Pattern;

public final class ResetToken {

    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Pattern WELL_FORMED = Pattern.compile("[A-Za-z0-9_-]{"
            + ENCODER.encodeToString(new byte[PasswordResetPolicy.TOKEN_BYTES]).length() + "}");

    private final String value;

    private ResetToken(String value) {
        this.value = value;
    }

    public static ResetToken generate(SecureRandom random) {
        byte[] bytes = new byte[PasswordResetPolicy.TOKEN_BYTES];
        random.nextBytes(bytes);
        return new ResetToken(ENCODER.encodeToString(bytes));
    }

    public static String hash(String raw) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256을 쓸 수 없습니다.", e);
        }
    }

    public static boolean isWellFormed(String raw) {
        return raw != null && WELL_FORMED.matcher(raw).matches();
    }

    public String value() {
        return value;
    }

    @Override
    public String toString() {
        return "ResetToken[****]";
    }
}
