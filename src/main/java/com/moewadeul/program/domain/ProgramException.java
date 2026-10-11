package com.moewadeul.program.domain;

import com.moewadeul.common.error.BusinessException;

public class ProgramException extends BusinessException {

    public ProgramException(ProgramErrorCode errorCode) {
        super(errorCode);
    }
}
