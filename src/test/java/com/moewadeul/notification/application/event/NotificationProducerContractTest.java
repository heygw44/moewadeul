package com.moewadeul.notification.application.event;

import static org.assertj.core.api.Assertions.assertThat;

import com.moewadeul.basecamp.application.BasecampMembershipEvents;
import com.moewadeul.basecamp.application.lifecycle.BasecampTransitionEvents;
import com.moewadeul.basecamp.application.membership.BasecampApplicationEvents;
import com.moewadeul.basecamp.application.membership.BasecampDecisionEvents;
import com.moewadeul.notification.application.NotificationEventTypes;
import com.moewadeul.program.application.ProgramApplicationEvents;
import com.moewadeul.program.application.enrollment.ProgramSeatEvents;
import com.moewadeul.trust.application.report.MemberReportEvents;
import com.moewadeul.trust.application.sanction.SanctionEvents;
import java.lang.reflect.RecordComponent;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class NotificationProducerContractTest {

    private record Component(String name, Type type) {}

    static Stream<Arguments> contracts() {
        return Stream.of(
                row(
                        NotificationEventTypes.BASECAMP_APPLIED,
                        BasecampApplicationEvents.EVENT_TYPE,
                        NotificationPayloads.Applied.class,
                        BasecampApplicationEvents.Payload.class),
                row(
                        NotificationEventTypes.BASECAMP_APPROVED,
                        BasecampDecisionEvents.APPROVED_EVENT_TYPE,
                        NotificationPayloads.Decision.class,
                        BasecampDecisionEvents.Payload.class),
                row(
                        NotificationEventTypes.BASECAMP_REJECTED,
                        BasecampDecisionEvents.REJECTED_EVENT_TYPE,
                        NotificationPayloads.Decision.class,
                        BasecampDecisionEvents.Payload.class),
                row(
                        NotificationEventTypes.BASECAMP_KICKED,
                        BasecampMembershipEvents.KICKED_EVENT_TYPE,
                        NotificationPayloads.Kicked.class,
                        BasecampMembershipEvents.KickedPayload.class),
                row(
                        NotificationEventTypes.BASECAMP_MEMBER_CHANGED,
                        BasecampMembershipEvents.MEMBER_CHANGED_EVENT_TYPE,
                        NotificationPayloads.BasecampMembers.class,
                        BasecampMembershipEvents.MemberChangedPayload.class),
                row(
                        NotificationEventTypes.BASECAMP_CONFIRMED,
                        BasecampTransitionEvents.CONFIRMED_EVENT_TYPE,
                        NotificationPayloads.BasecampMembers.class,
                        BasecampTransitionEvents.Payload.class),
                row(
                        NotificationEventTypes.BASECAMP_CANCELED,
                        BasecampTransitionEvents.CANCELED_EVENT_TYPE,
                        NotificationPayloads.BasecampMembers.class,
                        BasecampTransitionEvents.Payload.class),
                row(
                        NotificationEventTypes.BASECAMP_COMPLETED,
                        BasecampTransitionEvents.COMPLETED_EVENT_TYPE,
                        NotificationPayloads.BasecampMembers.class,
                        BasecampTransitionEvents.Payload.class),
                row(
                        NotificationEventTypes.MEMBER_REPORT_RESOLVED,
                        MemberReportEvents.RESOLVED_EVENT_TYPE,
                        NotificationPayloads.ReportResolved.class,
                        MemberReportEvents.ResolvedPayload.class),
                row(
                        NotificationEventTypes.SANCTION_CONFIRMED,
                        SanctionEvents.NOTIFICATION_EVENT_TYPE,
                        NotificationPayloads.SanctionConfirmed.class,
                        SanctionEvents.NotificationPayload.class),
                row(
                        NotificationEventTypes.PROGRAM_APPLICATION_CONFIRMED,
                        ProgramApplicationEvents.CONFIRMED_EVENT_TYPE,
                        NotificationPayloads.ProgramApplicationConfirmed.class,
                        ProgramApplicationEvents.ConfirmedPayload.class),
                row(
                        NotificationEventTypes.PROGRAM_APPLICATION_CANCELED,
                        ProgramApplicationEvents.CANCELED_EVENT_TYPE,
                        NotificationPayloads.ProgramApplicationCanceled.class,
                        ProgramApplicationEvents.CanceledPayload.class),
                row(
                        NotificationEventTypes.PROGRAM_APPLICATION_EXPIRED,
                        ProgramApplicationEvents.EXPIRED_EVENT_TYPE,
                        NotificationPayloads.ProgramApplicationExpired.class,
                        ProgramApplicationEvents.ExpiredPayload.class),
                row(
                        NotificationEventTypes.PROGRAM_SEAT_RELEASED,
                        ProgramSeatEvents.SEAT_RELEASED_EVENT_TYPE,
                        NotificationPayloads.ProgramSeatReleased.class,
                        ProgramSeatEvents.SeatReleasedPayload.class));
    }

    private static Arguments row(
            String consumerEventType, String producerEventType, Class<?> consumerPayload, Class<?> producerPayload) {
        return Arguments.of(consumerEventType, producerEventType, consumerPayload, producerPayload);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("contracts")
    @DisplayName("알림이 구독하는 이벤트 이름은 발행하는 쪽의 이름과 같다")
    void eventTypeMatchesProducer(
            String consumerEventType, String producerEventType, Class<?> consumerPayload, Class<?> producerPayload) {
        // given
        String expected = producerEventType;

        // when
        String actual = consumerEventType;

        // then
        assertThat(actual).as("알림 이벤트 %s의 발행 이름", consumerEventType).isEqualTo(expected);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("contracts")
    @DisplayName("알림이 읽는 payload의 필드 이름·순서·타입은 발행하는 payload와 같다")
    void payloadMatchesProducer(
            String consumerEventType, String producerEventType, Class<?> consumerPayload, Class<?> producerPayload) {
        // given
        List<Component> expected = components(producerPayload);

        // when
        List<Component> actual = components(consumerPayload);

        // then
        assertThat(actual)
                .as(
                        "알림 이벤트 %s의 payload %s, %s",
                        consumerEventType, consumerPayload.getSimpleName(), producerPayload.getName())
                .isEqualTo(expected);
    }

    @Test
    @DisplayName("메일 설정 대상 이벤트는 모두 계약 표에 있고 표에 없는 이벤트는 설정 대상이 아니다")
    void contractsCoverEveryConfigurableEvent() {
        // given
        List<String> covered =
                contracts().map(arguments -> (String) arguments.get()[0]).toList();

        // when
        List<String> configurable = NotificationEventTypes.EMAIL_CONFIGURABLE;

        // then
        assertThat(covered).containsExactlyInAnyOrderElementsOf(configurable);
    }

    private static List<Component> components(Class<?> payloadType) {
        return Arrays.stream(payloadType.getRecordComponents())
                .map((RecordComponent c) -> new Component(c.getName(), c.getGenericType()))
                .toList();
    }
}
