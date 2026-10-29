package com.moewadeul.notification.application.event;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import com.moewadeul.common.mail.MailSender;
import com.moewadeul.common.scheduling.ShutdownSignal;
import com.moewadeul.common.web.WebProperties;
import java.time.Clock;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronizationManager;

class NotificationMailerTest {

    private final NotificationMailStore store = mock(NotificationMailStore.class);
    private final MailSender mailSender = mock(MailSender.class);
    private final NotificationMailer mailer = new NotificationMailer(
            store, mailSender, Clock.systemUTC(), new WebProperties("https://moewadeul.example"), new ShutdownSignal());

    @AfterEach
    void clearTransaction() {
        TransactionSynchronizationManager.setActualTransactionActive(false);
    }

    @Test
    @DisplayName("알림 메일 발송을 트랜잭션 안에서 부르면 메일 대상을 잡기 전에 거부한다")
    void rejectsTransaction() {
        TransactionSynchronizationManager.setActualTransactionActive(true);

        assertThatThrownBy(() -> mailer.sendDue())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageStartingWith("트랜잭션 밖에서 불러야 합니다.");
        verifyNoInteractions(store, mailSender);
    }
}
