package com.moewadeul.member.domain;

import com.moewadeul.common.error.BusinessException;

public class MemberException extends BusinessException {

    public MemberException(MemberErrorCode errorCode) {
        super(errorCode);
    }
}
