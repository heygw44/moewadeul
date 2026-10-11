package com.moewadeul.basecamp.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.moewadeul.common.error.BusinessException;
import com.moewadeul.common.error.ErrorCode;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;

final class BasecampAssertions {

    private BasecampAssertions() {}

    static void assertFailsWith(ErrorCode expected, ThrowingCallable action) {
        assertThatThrownBy(action)
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(expected));
    }

    static void assertFailsWithInvalidState(ThrowingCallable action) {
        assertFailsWith(BasecampErrorCode.BASECAMP_INVALID_STATE, action);
    }
}
