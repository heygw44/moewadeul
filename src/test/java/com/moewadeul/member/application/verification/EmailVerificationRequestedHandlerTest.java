package com.moewadeul.member.application.verification;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import com.moewadeul.common.mail.MailSender;
import com.moewadeul.common.outbox.OutboxMessage;
import java.time.Instant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.databind.json.JsonMapper;

class EmailVerificationRequestedHandlerTest {

    private final EmailVerificationService emailVerificationService = mock(EmailVerificationService.class);
    private final MailSender mailSender = mock(MailSender.class);
    private final EmailVerificationRequestedHandler handler = new EmailVerificationRequestedHandler(
            emailVerificationService, mailSender, JsonMapper.builder().build());

    @AfterEach
    void clearTransaction() {
        TransactionSynchronizationManager.setActualTransactionActive(false);
    }

    @Test
    @DisplayName("이메일 인증 메일 발송을 트랜잭션 안에서 부르면 인증 코드를 발급하기 전에 거부한다")
    void rejectsTransaction() {
        TransactionSynchronizationManager.setActualTransactionActive(true);

        assertThatThrownBy(() -> handler.handle(
                        new OutboxMessage(1L, "EmailVerificationRequested", "Member", 2L, "{}", Instant.EPOCH)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageStartingWith("트랜잭션 밖에서 불러야 합니다.");
        verifyNoInteractions(emailVerificationService, mailSender);
    }
}
