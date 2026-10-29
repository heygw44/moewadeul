package com.moewadeul.common.mail;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willAnswer;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import jakarta.mail.AuthenticationFailedException;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;
import java.net.SocketTimeoutException;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.MailAuthenticationException;
import org.springframework.mail.MailException;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;

class SmtpMailSenderTest {

    private static final String FROM = "moewadeul@example.com";
    private static final String FROM_NAME = "뫼와들";
    private static final String RECIPIENT = "hiker@example.com";
    private static final String SUBJECT = "[뫼와들] 이메일 인증 코드";
    private static final String BODY = "인증 코드는 123456입니다.";

    private static final Duration SEND_TIMEOUT = Duration.ofSeconds(20);

    private final JavaMailSender javaMailSender = mock(JavaMailSender.class);
    private final SmtpMailSender mailSender =
            new SmtpMailSender(javaMailSender, new MailProperties(FROM, FROM_NAME, SEND_TIMEOUT));

    @BeforeEach
    void stubMimeMessageCreation() {
        JavaMailSenderImpl messageFactory = new JavaMailSenderImpl();
        given(javaMailSender.createMimeMessage()).willAnswer(invocation -> messageFactory.createMimeMessage());
    }

    @Test
    @DisplayName("[F-01] 발신자, 수신자, 제목, 본문을 담은 메일을 JavaMailSender로 보낸다")
    void sendsMessageWithFromToSubjectAndBody() throws Exception {
        mailSender.send(new MailMessage(RECIPIENT, SUBJECT, BODY));

        MimeMessage sent = captureSentMessage();
        assertThat(sent.getFrom()).containsExactly(new InternetAddress(FROM));
        assertThat(sent.getRecipients(Message.RecipientType.TO)).containsExactly(new InternetAddress(RECIPIENT));
        assertThat(sent.getSubject()).isEqualTo(SUBJECT);
        assertThat(sent.getContent()).isEqualTo(BODY);
    }

    @Test
    @DisplayName("[F-01] 제목과 본문을 UTF-8로 인코딩하고 한글이 그대로 복원된다")
    void encodesSubjectAndBodyAsUtf8() throws Exception {
        mailSender.send(new MailMessage(RECIPIENT, SUBJECT, BODY));

        MimeMessage sent = captureSentMessage();
        sent.saveChanges();
        assertThat(sent.getContentType()).containsIgnoringCase("charset=UTF-8");
        assertThat(sent.getContent()).isNotInstanceOf(MimeMultipart.class);
        assertThat(sent.getHeader("Subject", null)).contains("=?UTF-8?");
        assertThat(sent.getSubject()).isEqualTo(SUBJECT);
    }

    @Test
    @DisplayName("[F-01] 발신자에 서비스 이름을 UTF-8로 인코딩해 붙이고, 받는 쪽에서 한글 이름이 그대로 복원된다")
    void addsEncodedSenderName() throws Exception {
        mailSender.send(new MailMessage(RECIPIENT, SUBJECT, BODY));

        MimeMessage sent = captureSentMessage();
        sent.saveChanges();
        InternetAddress sender = (InternetAddress) sent.getFrom()[0];
        assertThat(sender.getAddress()).isEqualTo(FROM);
        assertThat(sender.getPersonal()).isEqualTo(FROM_NAME);
        assertThat(sent.getHeader("From", null)).contains("=?UTF-8?");
    }

    @Test
    @DisplayName("[F-01] 전송이 실패하면 원인을 유지한 MailDeliveryException을 던지고, 메시지에 수신자와 본문을 담지 않는다")
    void wrapsTransportFailureWithoutRecipientInMessage() {
        MailSendException transportFailure = new MailSendException("550 5.1.1 <" + RECIPIENT + "> user unknown");
        willThrow(transportFailure).given(javaMailSender).send(any(MimeMessage.class));

        assertThatThrownBy(() -> mailSender.send(new MailMessage(RECIPIENT, SUBJECT, BODY)))
                .isInstanceOf(MailDeliveryException.class)
                .hasCause(transportFailure)
                .message()
                .doesNotContain(RECIPIENT)
                .doesNotContain(BODY);
    }

    @Test
    @DisplayName("[F-01] 수신자 주소 형식이 잘못되어도 MailDeliveryException으로 바꿔 던지고, 메시지에 수신자를 담지 않는다")
    void wrapsInvalidRecipientAsDeliveryFailure() {
        String invalidRecipient = "not an address@@";

        assertThatThrownBy(() -> mailSender.send(new MailMessage(invalidRecipient, SUBJECT, BODY)))
                .isInstanceOf(MailDeliveryException.class)
                .message()
                .doesNotContain(invalidRecipient);
    }

    @Test
    @DisplayName("[F-20] 메일 서버에 연결하는 동안 제한 시간이 지나면 일시적인 실패로 표시한다")
    void marksConnectionTimeoutAsTransient() {
        MessagingException connectFailure =
                new MessagingException("Couldn't connect to host", new SocketTimeoutException("connect timed out"));

        assertThat(failureOf(new MailSendException("Mail server connection failed", connectFailure, Map.of())))
                .isTrue();
    }

    @Test
    @DisplayName("[F-20] 메일 서버가 4xx로 답하면 일시적인 실패로 표시한다")
    void marksTemporaryReplyAsTransient() {
        MessagingException temporaryReply = new MessagingException("451 4.3.0 Temporary server error");

        assertThat(failureOf(new MailSendException(Map.of("message", temporaryReply))))
                .isTrue();
    }

    @Test
    @DisplayName("[F-20] 메일 서버가 5xx로 답하면 일시적인 실패로 표시하지 않는다")
    void doesNotMarkPermanentReplyAsTransient() {
        MessagingException permanentReply = new MessagingException("550 5.1.1 <" + RECIPIENT + "> user unknown");

        assertThat(failureOf(new MailSendException(Map.of("message", permanentReply))))
                .isFalse();
    }

    @Test
    @DisplayName("[F-20] SMTP 로그인이 거부되면 일시적인 실패로 표시하지 않는다")
    void doesNotMarkAuthenticationFailureAsTransient() {
        AuthenticationFailedException rejected =
                new AuthenticationFailedException("535 5.7.8 Username and Password not accepted");

        assertThat(failureOf(new MailAuthenticationException(rejected))).isFalse();
    }

    @Test
    @DisplayName("[F-20] 메일 한 통을 보내는 시간이 상한을 넘으면 일시적인 실패로 끝내고 보내던 스레드를 멈춘다")
    void failsTransientlyAndStopsSendingWhenSendExceedsTimeout() throws Exception {
        // given
        CountDownLatch sendingInterrupted = new CountDownLatch(1);
        willAnswer(invocation -> {
                    try {
                        Thread.sleep(Duration.ofSeconds(30));
                    } catch (InterruptedException e) {
                        sendingInterrupted.countDown();
                    }
                    return null;
                })
                .given(javaMailSender)
                .send(any(MimeMessage.class));
        SmtpMailSender limited =
                new SmtpMailSender(javaMailSender, new MailProperties(FROM, FROM_NAME, Duration.ofMillis(100)));

        // when & then
        assertThatThrownBy(() -> limited.send(new MailMessage(RECIPIENT, SUBJECT, BODY)))
                .isInstanceOfSatisfying(
                        MailDeliveryException.class,
                        e -> assertThat(e.isTransientFailure()).isTrue())
                .message()
                .doesNotContain(RECIPIENT);
        assertThat(sendingInterrupted.await(5, TimeUnit.SECONDS)).isTrue();
        limited.destroy();
    }

    @Test
    @DisplayName("MailMessage의 toString은 수신자와 본문을 드러내지 않는다")
    void mailMessageToStringHidesValues() {
        String text = new MailMessage(RECIPIENT, SUBJECT, BODY).toString();

        assertThat(text).doesNotContain(RECIPIENT).doesNotContain(BODY);
    }

    private boolean failureOf(MailException transportFailure) {
        willThrow(transportFailure).given(javaMailSender).send(any(MimeMessage.class));
        try {
            mailSender.send(new MailMessage(RECIPIENT, SUBJECT, BODY));
        } catch (MailDeliveryException e) {
            return e.isTransientFailure();
        }
        throw new AssertionError("MailDeliveryException was not thrown");
    }

    private MimeMessage captureSentMessage() {
        ArgumentCaptor<MimeMessage> captor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(javaMailSender).send(captor.capture());
        return captor.getValue();
    }
}
