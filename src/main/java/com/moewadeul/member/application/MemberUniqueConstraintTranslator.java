package com.moewadeul.member.application;

import com.moewadeul.member.domain.MemberErrorCode;
import com.moewadeul.member.domain.MemberException;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.dao.DataIntegrityViolationException;

public final class MemberUniqueConstraintTranslator {

    private static final String EMAIL_UNIQUE_CONSTRAINT = "uk_member_email";
    private static final String NICKNAME_UNIQUE_CONSTRAINT = "uk_member_nickname";
    private static final String KEY_MARKER = " for key '";

    private MemberUniqueConstraintTranslator() {}

    public static RuntimeException translate(DataIntegrityViolationException e) {
        String violatedKey =
                violatedKey(NestedExceptionUtils.getMostSpecificCause(e).getMessage());
        if (violatedKey.contains(EMAIL_UNIQUE_CONSTRAINT)) {
            return new MemberException(MemberErrorCode.MEMBER_EMAIL_DUPLICATED);
        }
        if (violatedKey.contains(NICKNAME_UNIQUE_CONSTRAINT)) {
            return new MemberException(MemberErrorCode.MEMBER_NICKNAME_DUPLICATED);
        }
        return e;
    }

    // MySQL 메시지는 "Duplicate entry '값' for key '제약'" 형태라서 값에도 제약 이름이 들어갈 수 있다.
    // 예를 들어 닉네임이 uk_member_email이면 닉네임 중복인데 이메일 중복으로 읽는다. 그래서 값은 빼고 마지막 "for key" 뒤만 본다.
    private static String violatedKey(String message) {
        if (message == null) {
            return "";
        }
        int marker = message.lastIndexOf(KEY_MARKER);
        return marker < 0 ? message : message.substring(marker + KEY_MARKER.length());
    }
}
