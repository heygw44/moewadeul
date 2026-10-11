package com.moewadeul.common.error;

import org.springframework.http.HttpStatus;

public interface ErrorCode {

    String name();

    HttpStatus httpStatus();

    String message();
}
