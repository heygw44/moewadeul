package com.moewadeul.basecamp.domain;

import com.moewadeul.common.error.BusinessException;

public class BasecampException extends BusinessException {

    public BasecampException(BasecampErrorCode errorCode) {
        super(errorCode);
    }
}
