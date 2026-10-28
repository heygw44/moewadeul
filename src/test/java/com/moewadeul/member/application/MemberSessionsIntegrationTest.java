package com.moewadeul.member.application;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.moewadeul.common.testsupport.IntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

@IntegrationTest
class MemberSessionsIntegrationTest {

    private static final long MEMBER_ID = 1L;

    @Autowired
    private MemberSessions memberSessions;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Test
    @DisplayName("트랜잭션 안에서 회원 세션을 지우려 하면 커넥션을 더 잡기 전에 거부한다")
    void invalidateAll_insideTransaction_isRejected() {
        // when & then
        assertThatThrownBy(() ->
                        transactionTemplate.executeWithoutResult(status -> memberSessions.invalidateAll(MEMBER_ID)))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("트랜잭션 밖에서는 회원 세션을 지운다")
    void invalidateAll_outsideTransaction_succeeds() {
        // when & then
        assertThatCode(() -> memberSessions.invalidateAll(MEMBER_ID)).doesNotThrowAnyException();
    }
}
