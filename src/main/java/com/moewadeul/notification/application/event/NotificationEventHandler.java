package com.moewadeul.notification.application.event;

import com.moewadeul.common.outbox.OutboxEventHandler;
import com.moewadeul.common.outbox.OutboxMessage;
import java.util.List;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

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

    abstract List<NotificationDraft> drafts(OutboxMessage message, P payload);

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
