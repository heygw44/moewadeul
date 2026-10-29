package com.moewadeul.member.application.password;

import com.moewadeul.common.mail.MailSender;
import com.moewadeul.common.outbox.OutboxEventHandler;
import com.moewadeul.common.outbox.OutboxMessage;
import java.util.Optional;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
class PasswordResetRequestedHandler implements OutboxEventHandler {

    private static final String LINK_PATH = "/password-reset#token=";

    private final PasswordResetRequestService passwordResetRequestService;
    private final MailSender mailSender;
    private final String baseUrl;

    PasswordResetRequestedHandler(
            PasswordResetRequestService passwordResetRequestService,
            MailSender mailSender,
            @Value("${moewadeul.web.base-url}") String baseUrl) {
        this.passwordResetRequestService = passwordResetRequestService;
        this.mailSender = mailSender;
        this.baseUrl = stripTrailingSlashes(baseUrl);
    }

    @Override
    public Set<String> eventTypes() {
        return Set.of(PasswordResetEvents.EVENT_TYPE);
    }

    @Override
    public void handle(OutboxMessage message) {
        Optional<IssuedPasswordReset> issued =
                passwordResetRequestService.issueFor(message.aggregateId(), message.eventId());
        issued.ifPresent(this::sendMail);
    }

    private void sendMail(IssuedPasswordReset issued) {
        mailSender.send(PasswordResetMailFactory.create(issued.email(), link(issued.token())));
    }

    // 토큰을 쿼리 문자열이 아니라 프래그먼트(#)에 싣는다. 브라우저는 프래그먼트를 서버로 보내지 않아서
    // 접근 로그, 프록시 로그, Referer에 토큰이 남지 않는다.
    private String link(String token) {
        return baseUrl + LINK_PATH + token;
    }

    private static String stripTrailingSlashes(String url) {
        int end = url.length();
        while (end > 0 && url.charAt(end - 1) == '/') {
            end--;
        }
        return url.substring(0, end);
    }
}
