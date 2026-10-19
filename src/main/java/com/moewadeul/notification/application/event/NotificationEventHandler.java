package com.moewadeul.notification.application.event;

import com.moewadeul.common.outbox.OutboxEventHandler;
import com.moewadeul.common.outbox.OutboxMessage;
import java.util.List;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/**
 * 이벤트를 알림으로 바꿔 저장하는 처리기의 공통 흐름이다. payload를 읽고, 하위 클래스가 만든 알림 내용을 {@link NotificationWriter}에
 * 넘겨 저장한다. 메일은 여기서 보내지 않는다. 저장한 알림의 메일은 {@link NotificationMailer}가 따로 보낸다.
 *
 * <p>예전에는 저장한 뒤 메일까지 여기서 보냈다. 그런데 발행기는 이벤트를 차례로 처리하므로, 받는 회원이 수천 명인 이벤트 하나가 메일을
 * 보내는 동안 다른 이벤트가 모두 기다렸다. 그래서 이벤트 처리는 알림 저장까지만 하고 짧게 끝낸다.
 *
 * <p>이 클래스에는 {@code @Transactional}을 걸지 않는다. 저장은 {@link NotificationWriter}가 자기 트랜잭션으로 끝낸다.
 * payload를 읽지 못하거나 저장에 실패하면 예외가 발행기로 올라가 이벤트를 다시 처리한다. 같은 이벤트가 다시 와도
 * {@link NotificationWriter}가 이미 저장된 알림을 건너뛰므로 알림은 한 번만 생긴다.
 */
abstract class NotificationEventHandler<P> implements OutboxEventHandler {

    private final String eventType;
    private final Class<P> payloadType;
    private final JsonMapper jsonMapper;
    private final NotificationWriter notificationWriter;

    NotificationEventHandler(
            String eventType, Class<P> payloadType, JsonMapper jsonMapper, NotificationWriter notificationWriter) {
        this.eventType = eventType;
        this.payloadType = payloadType;
        this.jsonMapper = jsonMapper;
        this.notificationWriter = notificationWriter;
    }

    @Override
    public String eventType() {
        return eventType;
    }

    @Override
    public void handle(OutboxMessage message) {
        P payload = parse(message);
        List<NotificationDraft> drafts = drafts(message, payload);
        notificationWriter.write(message.eventId(), drafts);
    }

    /** 이벤트에서 받는 사람마다 알림 내용을 만든다. message는 payload에 없는 값(예: 집합체 ID)이 필요할 때 쓴다. */
    abstract List<NotificationDraft> drafts(OutboxMessage message, P payload);

    // payload에는 회원 ID가 들어 있으므로 예외 메시지에 내용을 싣지 않는다.
    private P parse(OutboxMessage message) {
        try {
            P payload = jsonMapper.readValue(message.payload(), payloadType);
            if (payload == null) {
                throw new IllegalArgumentException(eventType + " 이벤트 payload가 비어 있습니다. eventId=" + message.eventId());
            }
            return payload;
        } catch (JacksonException e) {
            throw new IllegalArgumentException(eventType + " 이벤트 payload를 읽을 수 없습니다. eventId=" + message.eventId());
        }
    }
}
