package com.moewadeul.member.application;

import com.moewadeul.common.error.UniqueViolations;
import com.moewadeul.member.domain.MemberErrorCode;
import com.moewadeul.member.domain.MemberException;
import org.springframework.dao.DataIntegrityViolationException;

public final class MemberUniqueConstraintTranslator {

    private static final String EMAIL_UNIQUE_CONSTRAINT = "uk_member_email";
    private static final String NICKNAME_UNIQUE_CONSTRAINT = "uk_member_nickname";

    private MemberUniqueConstraintTranslator() {}

    public static RuntimeException translate(DataIntegrityViolationException e) {
        if (UniqueViolations.violates(e, EMAIL_UNIQUE_CONSTRAINT)) {
            return new MemberException(MemberErrorCode.MEMBER_EMAIL_DUPLICATED);
        }
        if (UniqueViolations.violates(e, NICKNAME_UNIQUE_CONSTRAINT)) {
            return new MemberException(MemberErrorCode.MEMBER_NICKNAME_DUPLICATED);
        }
        return e;
    }
}
