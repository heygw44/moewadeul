package com.moewadeul.member.application.verification;

import com.moewadeul.common.mail.MailSender;
import com.moewadeul.common.outbox.OutboxEventHandler;
import com.moewadeul.common.outbox.OutboxMessage;
import com.moewadeul.common.transaction.OutsideTransaction;
import com.moewadeul.member.application.EmailVerificationEvents;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

@Component
@RequiredArgsConstructor
class EmailVerificationRequestedHandler implements OutboxEventHandler {

    private final EmailVerificationService emailVerificationService;
    private final MailSender mailSender;
    private final JsonMapper jsonMapper;

    @Override
    public Set<String> eventTypes() {
        return Set.of(EmailVerificationEvents.EVENT_TYPE);
    }

    @Override
    public void handle(OutboxMessage message) {
        OutsideTransaction.require("메일 서버 응답을 기다리는 동안 DB 커넥션을 쥐면 안 된다.");
        EmailVerificationEvents.Payload payload = parse(message);
        Optional<IssuedVerification> issued =
                emailVerificationService.issueFor(message.aggregateId(), payload.requestIp(), message.eventId());
        issued.ifPresent(this::sendMail);
    }

    private void sendMail(IssuedVerification issued) {
        mailSender.send(EmailVerificationMailFactory.create(issued.email(), issued.code()));
    }

    private EmailVerificationEvents.Payload parse(OutboxMessage message) {
        try {
            EmailVerificationEvents.Payload payload =
                    jsonMapper.readValue(message.payload(), EmailVerificationEvents.Payload.class);
            if (payload == null) {
                throw new IllegalArgumentException("이메일 인증 이벤트 payload가 비어 있습니다. eventId=" + message.eventId());
            }
            return payload;
        } catch (JacksonException e) {
            throw new IllegalArgumentException("이메일 인증 이벤트 payload를 읽을 수 없습니다. eventId=" + message.eventId());
        }
    }
}
