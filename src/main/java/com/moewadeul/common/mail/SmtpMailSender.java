package com.moewadeul.common.mail;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

/**
 * SMTP로 메일을 보낸다. 연결·읽기·쓰기 제한 시간은 {@code spring.mail.properties.mail.smtp.*} 설정이 정한다.
 *
 * <p>발신 주소는 {@code moewadeul.mail.from}이고, 설정이 없으면 SMTP 계정({@code spring.mail.username})을 쓴다.
 * Gmail은 로그인한 계정과 다른 발신 주소를 계정 주소로 바꿔 보내기 때문이다. 받는 사람의 메일함에는 주소 대신
 * {@code moewadeul.mail.from-name}에 둔 서비스 이름이 보인다.
 *
 * <p>실패 원인에는 수신자 주소가 들어 있을 수 있어서, 예외 메시지에는 원인의 클래스 이름만 남긴다.
 */
@Component
public class SmtpMailSender implements MailSender {

    private final JavaMailSender javaMailSender;
    private final String from;
    private final String fromName;

    public SmtpMailSender(
            JavaMailSender javaMailSender,
            @Value("${moewadeul.mail.from}") String from,
            @Value("${moewadeul.mail.from-name}") String fromName) {
        this.javaMailSender = javaMailSender;
        this.from = from;
        this.fromName = fromName;
    }

    @Override
    public void send(MailMessage message) {
        try {
            javaMailSender.send(toMimeMessage(message));
        } catch (MessagingException | UnsupportedEncodingException | MailException e) {
            throw new MailDeliveryException(
                    "mail delivery failed: " + e.getClass().getSimpleName(), e);
        }
    }

    private MimeMessage toMimeMessage(MailMessage message) throws MessagingException, UnsupportedEncodingException {
        MimeMessage mimeMessage = javaMailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, StandardCharsets.UTF_8.name());
        helper.setFrom(from, fromName);
        helper.setTo(message.to());
        helper.setSubject(message.subject());
        helper.setText(message.body(), false);
        return mimeMessage;
    }
}
