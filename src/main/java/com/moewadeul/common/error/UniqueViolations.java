package com.moewadeul.common.error;

import org.springframework.core.NestedExceptionUtils;
import org.springframework.dao.DataIntegrityViolationException;

public final class UniqueViolations {

    private static final String KEY_MARKER = " for key '";

    private UniqueViolations() {}

    // MySQL 메시지는 "Duplicate entry '값' for key '제약'" 형태라서 값에도 제약 이름이 들어갈 수 있다.
    // 예를 들어 닉네임이 uk_member_email이면 닉네임 중복인데 이메일 중복으로 읽는다. 그래서 값은 빼고 마지막 "for key" 뒤만 본다.
    public static boolean violates(DataIntegrityViolationException e, String constraintName) {
        String message = NestedExceptionUtils.getMostSpecificCause(e).getMessage();
        if (message == null) {
            return false;
        }
        int marker = message.lastIndexOf(KEY_MARKER);
        String violatedKey = marker < 0 ? message : message.substring(marker + KEY_MARKER.length());
        return violatedKey.contains(constraintName);
    }
}
