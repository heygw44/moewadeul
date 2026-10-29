package com.moewadeul.common.error;

import org.springframework.core.NestedExceptionUtils;
import org.springframework.dao.DataIntegrityViolationException;

public final class UniqueViolations {

    private static final String KEY_MARKER = " for key '";

    private UniqueViolations() {}

    public static RuntimeException translate(
            DataIntegrityViolationException e, String constraintName, ErrorCode errorCode) {
        return violates(e, constraintName) ? new BusinessException(errorCode) : e;
    }

    // MySQL 메시지는 "Duplicate entry '값' for key '제약'" 형태라서 값에도 제약 이름이 들어갈 수 있다.
    // 예를 들어 닉네임이 uk_member_email이면 닉네임 중복인데 이메일 중복으로 읽는다. 그래서 값은 빼고 마지막 "for key" 뒤만 본다.
    public static boolean violates(DataIntegrityViolationException e, String constraintName) {
        String message = mostSpecificMessage(e);
        return message != null && violatedKey(message).contains(constraintName);
    }

    public static String redactedMessage(DataIntegrityViolationException e) {
        String message = mostSpecificMessage(e);
        if (message == null) {
            return "none";
        }
        return message.contains(KEY_MARKER) ? "Duplicate entry for key '" + violatedKey(message) : message;
    }

    private static String mostSpecificMessage(DataIntegrityViolationException e) {
        return NestedExceptionUtils.getMostSpecificCause(e).getMessage();
    }

    private static String violatedKey(String message) {
        int marker = message.lastIndexOf(KEY_MARKER);
        return marker < 0 ? message : message.substring(marker + KEY_MARKER.length());
    }
}
