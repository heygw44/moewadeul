package com.moewadeul.common.error;

import lombok.Getter;

@Getter
public class InvalidFieldException extends BusinessException {

    private final String field;

    public InvalidFieldException(String field, String reason) {
        super(CommonErrorCode.INVALID_INPUT, reason);
        this.field = field;
    }
}
