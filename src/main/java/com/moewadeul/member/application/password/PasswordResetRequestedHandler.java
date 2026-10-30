package com.moewadeul.member.application.password;

import com.moewadeul.common.mail.MailSender;
import com.moewadeul.common.outbox.OutboxEventHandler;
import com.moewadeul.common.outbox.OutboxLane;
import com.moewadeul.common.outbox.OutboxMessage;
import com.moewadeul.common.transaction.OutsideTransaction;
import com.moewadeul.common.web.WebProperties;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
class PasswordResetRequestedHandler implements OutboxEventHandler {

    private static final String LINK_PATH = "/password-reset#token=";

    private final PasswordResetRequestService passwordResetRequestService;
    private final MailSender mailSender;
    private final WebProperties webProperties;

    PasswordResetRequestedHandler(
            PasswordResetRequestService passwordResetRequestService,
            MailSender mailSender,
            WebProperties webProperties) {
        this.passwordResetRequestService = passwordResetRequestService;
        this.mailSender = mailSender;
        this.webProperties = webProperties;
    }

    @Override
    public Set<String> eventTypes() {
        return Set.of(PasswordResetEvents.EVENT_TYPE);
    }

    @Override
    public OutboxLane lane() {
        return OutboxLane.MAIL;
    }

    @Override
    public void handle(OutboxMessage message) {
        OutsideTransaction.require("메일 서버 응답을 기다리는 동안 DB 커넥션을 쥐면 안 된다.");
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
        return webProperties.baseUrl() + LINK_PATH + token;
    }
}
