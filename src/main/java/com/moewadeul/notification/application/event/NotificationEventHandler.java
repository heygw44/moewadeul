package com.moewadeul.notification.application.event;

import com.moewadeul.common.event.BasecampEvents;
import com.moewadeul.common.event.ProgramEvents;
import com.moewadeul.common.event.TrustEvents;
import com.moewadeul.common.outbox.OutboxEventHandler;
import com.moewadeul.common.outbox.OutboxMessage;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Function;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

@Component
class NotificationEventHandler implements OutboxEventHandler {

    private static final Map<String, Route<?>> ROUTES = Map.ofEntries(
            routeWithAggregateId(
                    BasecampEvents.APPLIED, BasecampEvents.AppliedPayload.class, NotificationTemplates::applied),
            route(BasecampEvents.APPROVED, BasecampEvents.DecisionPayload.class, NotificationTemplates::approved),
            route(BasecampEvents.REJECTED, BasecampEvents.DecisionPayload.class, NotificationTemplates::rejected),
            route(BasecampEvents.KICKED, BasecampEvents.KickedPayload.class, NotificationTemplates::kicked),
            route(
                    BasecampEvents.MEMBER_CHANGED,
                    BasecampEvents.MembersPayload.class,
                    NotificationTemplates::memberChanged),
            route(BasecampEvents.CONFIRMED, BasecampEvents.MembersPayload.class, NotificationTemplates::confirmed),
            route(BasecampEvents.CANCELED, BasecampEvents.MembersPayload.class, NotificationTemplates::canceled),
            route(BasecampEvents.COMPLETED, BasecampEvents.MembersPayload.class, NotificationTemplates::completed),
            route(
                    TrustEvents.REPORT_RESOLVED,
                    TrustEvents.ReportResolvedPayload.class,
                    NotificationTemplates::reportResolved),
            route(
                    TrustEvents.SANCTION_CONFIRMED,
                    TrustEvents.SanctionConfirmedPayload.class,
                    NotificationTemplates::sanctionConfirmed),
            route(
                    ProgramEvents.APPLICATION_CONFIRMED,
                    ProgramEvents.ApplicationPayload.class,
                    NotificationTemplates::programApplicationConfirmed),
            route(
                    ProgramEvents.APPLICATION_CANCELED,
                    ProgramEvents.CanceledPayload.class,
                    NotificationTemplates::programApplicationCanceled),
            route(
                    ProgramEvents.APPLICATION_EXPIRED,
                    ProgramEvents.ApplicationPayload.class,
                    NotificationTemplates::programApplicationExpired),
            route(
                    ProgramEvents.SEAT_RELEASED,
                    ProgramEvents.SeatReleasedPayload.class,
                    NotificationTemplates::programSeatReleased));

    private final JsonMapper jsonMapper;
    private final NotificationWriter notificationWriter;

    NotificationEventHandler(JsonMapper jsonMapper, NotificationWriter notificationWriter) {
        this.jsonMapper = jsonMapper;
        this.notificationWriter = notificationWriter;
    }

    @Override
    public Set<String> eventTypes() {
        return ROUTES.keySet();
    }

    @Override
    public void handle(OutboxMessage message) {
        List<NotificationDraft> drafts = ROUTES.get(message.eventType()).drafts(message, jsonMapper);
        notificationWriter.write(message.eventId(), drafts);
    }

    private static <P> Map.Entry<String, Route<?>> route(
            String eventType, Class<P> payloadType, Function<P, List<NotificationDraft>> template) {
        return routeWithAggregateId(eventType, payloadType, (aggregateId, payload) -> template.apply(payload));
    }

    private static <P> Map.Entry<String, Route<?>> routeWithAggregateId(
            String eventType, Class<P> payloadType, BiFunction<Long, P, List<NotificationDraft>> template) {
        return Map.entry(eventType, new Route<>(payloadType, template));
    }

    private record Route<P>(Class<P> payloadType, BiFunction<Long, P, List<NotificationDraft>> template) {

        List<NotificationDraft> drafts(OutboxMessage message, JsonMapper jsonMapper) {
            return template.apply(message.aggregateId(), parse(message, jsonMapper));
        }

        private P parse(OutboxMessage message, JsonMapper jsonMapper) {
            try {
                P payload = jsonMapper.readValue(message.payload(), payloadType);
                if (payload == null) {
                    throw new IllegalArgumentException(
                            message.eventType() + " 이벤트 payload가 비어 있습니다. eventId=" + message.eventId());
                }
                return payload;
            } catch (JacksonException e) {
                throw new IllegalArgumentException(
                        message.eventType() + " 이벤트 payload를 읽을 수 없습니다. eventId=" + message.eventId());
            }
        }
    }
}
