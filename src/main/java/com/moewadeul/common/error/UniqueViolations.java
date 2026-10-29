package com.moewadeul.common.error;

import java.sql.SQLException;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.dao.DataIntegrityViolationException;

public final class UniqueViolations {

    private static final String KEY_MARKER = " for key '";
    private static final int MYSQL_DUPLICATE_ENTRY = 1062;

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

    // JPA 경로에서는 고유 제약 위반도 DuplicateKeyException이 아니라 DataIntegrityViolationException으로 온다. 그래서 예외 타입 대신 MySQL 오류 코드를
    // 본다.
    public static boolean isDuplicateEntry(DataIntegrityViolationException e) {
        return NestedExceptionUtils.getMostSpecificCause(e) instanceof SQLException sqlException
                && sqlException.getErrorCode() == MYSQL_DUPLICATE_ENTRY;
    }

    public static String redactedMessage(DataIntegrityViolationException e) {
        String message = mostSpecificMessage(e);
        return message == null ? "none" : "Duplicate entry for key '" + violatedKey(message);
    }

    private static String mostSpecificMessage(DataIntegrityViolationException e) {
        return NestedExceptionUtils.getMostSpecificCause(e).getMessage();
    }

    private static String violatedKey(String message) {
        int marker = message.lastIndexOf(KEY_MARKER);
        return marker < 0 ? message : message.substring(marker + KEY_MARKER.length());
    }
}
