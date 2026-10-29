package com.moewadeul.member.application.password;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import com.moewadeul.common.mail.MailSender;
import com.moewadeul.common.outbox.OutboxMessage;
import com.moewadeul.common.web.WebProperties;
import java.time.Instant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronizationManager;

class PasswordResetRequestedHandlerTest {

    private final PasswordResetRequestService passwordResetRequestService = mock(PasswordResetRequestService.class);
    private final MailSender mailSender = mock(MailSender.class);
    private final PasswordResetRequestedHandler handler = new PasswordResetRequestedHandler(
            passwordResetRequestService, mailSender, new WebProperties("https://moewadeul.example"));

    @AfterEach
    void clearTransaction() {
        TransactionSynchronizationManager.setActualTransactionActive(false);
    }

    @Test
    @DisplayName("비밀번호 재설정 메일 발송을 트랜잭션 안에서 부르면 토큰을 발급하기 전에 거부한다")
    void rejectsTransaction() {
        TransactionSynchronizationManager.setActualTransactionActive(true);

        assertThatThrownBy(() -> handler.handle(
                        new OutboxMessage(1L, "PasswordResetRequested", "Member", 2L, "{}", Instant.EPOCH)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageStartingWith("트랜잭션 밖에서 불러야 합니다.");
        verifyNoInteractions(passwordResetRequestService, mailSender);
    }
}
