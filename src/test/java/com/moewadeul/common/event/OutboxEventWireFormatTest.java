package com.moewadeul.common.event;

import static org.assertj.core.api.Assertions.assertThat;

import com.moewadeul.basecamp.application.BasecampMembershipEvents;
import com.moewadeul.basecamp.application.lifecycle.BasecampTransitionEvents;
import com.moewadeul.basecamp.application.membership.BasecampApplicationEvents;
import com.moewadeul.basecamp.application.membership.BasecampDecisionEvents;
import com.moewadeul.member.application.EmailVerificationEvents;
import com.moewadeul.member.application.password.PasswordResetEvents;
import com.moewadeul.program.application.ProgramApplicationEvents;
import com.moewadeul.program.application.enrollment.ProgramSeatEvents;
import com.moewadeul.trust.application.report.MemberReportEvents;
import com.moewadeul.trust.application.sanction.SanctionEvents;
import com.moewadeul.trust.application.withdrawal.WithdrawalEvents;
import java.time.Instant;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.json.JsonTest;
import tools.jackson.databind.json.JsonMapper;

/**
 * outbox_event 행에는 이벤트 이름과 JSON payload가 그대로 남는다. 배포 전에 기록된 PENDING 행을 배포 뒤의 처리기가 읽어야 하므로 이름, 집합 종류,
 * JSON 필드 이름은 바꿀 수 없다. stored는 MySQL JSON 열이 정규화해 돌려주는 모양(키 길이순, ": "와 ", " 구분자)이다.
 */
@JsonTest
class OutboxEventWireFormatTest {

    @Autowired
    private JsonMapper jsonMapper;

    record Wire(
            String type,
            String typeConstant,
            String aggregate,
            String aggregateConstant,
            Object payload,
            String json,
            String stored) {

        @Override
        public String toString() {
            return type;
        }
    }

    static Stream<Wire> wires() {
        return Stream.of(
                new Wire(
                        "BASECAMP_APPLIED",
                        BasecampApplicationEvents.EVENT_TYPE,
                        "BASECAMP",
                        BasecampApplicationEvents.AGGREGATE_TYPE,
                        new BasecampApplicationEvents.Payload(1L, 2L, 3L),
                        "{\"applicationId\":1,\"applicantId\":2,\"leaderId\":3}",
                        "{\"leaderId\": 3, \"applicantId\": 2, \"applicationId\": 1}"),
                new Wire(
                        "BASECAMP_APPROVED",
                        BasecampDecisionEvents.APPROVED_EVENT_TYPE,
                        "BASECAMP",
                        BasecampDecisionEvents.AGGREGATE_TYPE,
                        new BasecampDecisionEvents.Payload(1L, 2L, 30L),
                        "{\"applicationId\":1,\"applicantId\":2,\"basecampId\":30}",
                        "{\"basecampId\": 30, \"applicantId\": 2, \"applicationId\": 1}"),
                new Wire(
                        "BASECAMP_REJECTED",
                        BasecampDecisionEvents.REJECTED_EVENT_TYPE,
                        "BASECAMP",
                        BasecampDecisionEvents.AGGREGATE_TYPE,
                        new BasecampDecisionEvents.Payload(1L, 2L, 30L),
                        "{\"applicationId\":1,\"applicantId\":2,\"basecampId\":30}",
                        "{\"basecampId\": 30, \"applicantId\": 2, \"applicationId\": 1}"),
                new Wire(
                        "BASECAMP_KICKED",
                        BasecampMembershipEvents.KICKED_EVENT_TYPE,
                        "BASECAMP",
                        BasecampMembershipEvents.AGGREGATE_TYPE,
                        new BasecampMembershipEvents.KickedPayload(30L, 4L, "NO_CONTACT"),
                        "{\"basecampId\":30,\"memberId\":4,\"reason\":\"NO_CONTACT\"}",
                        "{\"reason\": \"NO_CONTACT\", \"memberId\": 4, \"basecampId\": 30}"),
                new Wire(
                        "BASECAMP_MEMBER_CHANGED",
                        BasecampMembershipEvents.MEMBER_CHANGED_EVENT_TYPE,
                        "BASECAMP",
                        BasecampMembershipEvents.AGGREGATE_TYPE,
                        new BasecampMembershipEvents.MemberChangedPayload(30L, List.of(4L, 5L)),
                        "{\"basecampId\":30,\"memberIds\":[4,5]}",
                        "{\"memberIds\": [4, 5], \"basecampId\": 30}"),
                new Wire(
                        "BASECAMP_CONFIRMED",
                        BasecampTransitionEvents.CONFIRMED_EVENT_TYPE,
                        "BASECAMP",
                        BasecampTransitionEvents.AGGREGATE_TYPE,
                        new BasecampTransitionEvents.Payload(30L, List.of(4L, 5L)),
                        "{\"basecampId\":30,\"memberIds\":[4,5]}",
                        "{\"memberIds\": [4, 5], \"basecampId\": 30}"),
                new Wire(
                        "BASECAMP_CANCELED",
                        BasecampTransitionEvents.CANCELED_EVENT_TYPE,
                        "BASECAMP",
                        BasecampTransitionEvents.AGGREGATE_TYPE,
                        new BasecampTransitionEvents.Payload(30L, List.of(4L, 5L)),
                        "{\"basecampId\":30,\"memberIds\":[4,5]}",
                        "{\"memberIds\": [4, 5], \"basecampId\": 30}"),
                new Wire(
                        "BASECAMP_COMPLETED",
                        BasecampTransitionEvents.COMPLETED_EVENT_TYPE,
                        "BASECAMP",
                        BasecampTransitionEvents.AGGREGATE_TYPE,
                        new BasecampTransitionEvents.Payload(30L, List.of(4L, 5L)),
                        "{\"basecampId\":30,\"memberIds\":[4,5]}",
                        "{\"memberIds\": [4, 5], \"basecampId\": 30}"),
                new Wire(
                        "EMAIL_VERIFICATION_REQUESTED",
                        EmailVerificationEvents.EVENT_TYPE,
                        "MEMBER",
                        EmailVerificationEvents.AGGREGATE_TYPE,
                        new EmailVerificationEvents.Payload("203.0.113.7"),
                        "{\"requestIp\":\"203.0.113.7\"}",
                        "{\"requestIp\": \"203.0.113.7\"}"),
                new Wire(
                        "PASSWORD_RESET_REQUESTED",
                        PasswordResetEvents.EVENT_TYPE,
                        "MEMBER",
                        PasswordResetEvents.AGGREGATE_TYPE,
                        new PasswordResetEvents.Payload(),
                        "{}",
                        "{}"),
                new Wire(
                        "PROGRAM_APPLICATION_CONFIRMED",
                        ProgramApplicationEvents.CONFIRMED_EVENT_TYPE,
                        "PROGRAM_APPLICATION",
                        ProgramApplicationEvents.AGGREGATE_TYPE,
                        new ProgramApplicationEvents.ConfirmedPayload(1L, 2L, 3L),
                        "{\"applicationId\":1,\"memberId\":2,\"programId\":3}",
                        "{\"memberId\": 2, \"programId\": 3, \"applicationId\": 1}"),
                new Wire(
                        "PROGRAM_APPLICATION_CANCELED",
                        ProgramApplicationEvents.CANCELED_EVENT_TYPE,
                        "PROGRAM_APPLICATION",
                        ProgramApplicationEvents.AGGREGATE_TYPE,
                        new ProgramApplicationEvents.CanceledPayload(1L, 2L, 3L, "USER", true),
                        "{\"applicationId\":1,\"memberId\":2,\"programId\":3,\"reason\":\"USER\",\"refunded\":true}",
                        "{\"reason\": \"USER\", \"memberId\": 2, \"refunded\": true, \"programId\": 3,"
                                + " \"applicationId\": 1}"),
                new Wire(
                        "PROGRAM_APPLICATION_EXPIRED",
                        ProgramApplicationEvents.EXPIRED_EVENT_TYPE,
                        "PROGRAM_APPLICATION",
                        ProgramApplicationEvents.AGGREGATE_TYPE,
                        new ProgramApplicationEvents.ExpiredPayload(1L, 2L, 3L),
                        "{\"applicationId\":1,\"memberId\":2,\"programId\":3}",
                        "{\"memberId\": 2, \"programId\": 3, \"applicationId\": 1}"),
                new Wire(
                        "PROGRAM_SEAT_RELEASED",
                        ProgramSeatEvents.SEAT_RELEASED_EVENT_TYPE,
                        "PROGRAM",
                        ProgramSeatEvents.AGGREGATE_TYPE,
                        new ProgramSeatEvents.SeatReleasedPayload(3L, List.of(4L, 5L)),
                        "{\"programId\":3,\"memberIds\":[4,5]}",
                        "{\"memberIds\": [4, 5], \"programId\": 3}"),
                new Wire(
                        "MEMBER_REPORT_RESOLVED",
                        MemberReportEvents.RESOLVED_EVENT_TYPE,
                        "MEMBER_REPORT",
                        MemberReportEvents.AGGREGATE_TYPE,
                        new MemberReportEvents.ResolvedPayload(7L, 8L, "ACTIONED"),
                        "{\"reportId\":7,\"reporterId\":8,\"result\":\"ACTIONED\"}",
                        "{\"result\": \"ACTIONED\", \"reportId\": 7, \"reporterId\": 8}"),
                new Wire(
                        "SANCTION_BASECAMP_CLEANUP",
                        SanctionEvents.BASECAMP_CLEANUP_EVENT_TYPE,
                        "MEMBER",
                        SanctionEvents.AGGREGATE_TYPE,
                        new SanctionEvents.Payload(4L, 9L),
                        "{\"memberId\":4,\"sanctionId\":9}",
                        "{\"memberId\": 4, \"sanctionId\": 9}"),
                new Wire(
                        "SANCTION_PROGRAM_CLEANUP",
                        SanctionEvents.PROGRAM_CLEANUP_EVENT_TYPE,
                        "MEMBER",
                        SanctionEvents.AGGREGATE_TYPE,
                        new SanctionEvents.Payload(4L, 9L),
                        "{\"memberId\":4,\"sanctionId\":9}",
                        "{\"memberId\": 4, \"sanctionId\": 9}"),
                new Wire(
                        "SANCTION_CONFIRMED",
                        SanctionEvents.NOTIFICATION_EVENT_TYPE,
                        "MEMBER",
                        SanctionEvents.AGGREGATE_TYPE,
                        new SanctionEvents.NotificationPayload(
                                4L, 9L, "SUSPEND_7D", Instant.parse("2026-11-05T15:00:00Z")),
                        "{\"memberId\":4,\"sanctionId\":9,\"sanctionType\":\"SUSPEND_7D\","
                                + "\"endsAt\":\"2026-11-05T15:00:00Z\"}",
                        "{\"endsAt\": \"2026-11-05T15:00:00Z\", \"memberId\": 4, \"sanctionId\": 9,"
                                + " \"sanctionType\": \"SUSPEND_7D\"}"),
                new Wire(
                        "SANCTION_CONFIRMED",
                        SanctionEvents.NOTIFICATION_EVENT_TYPE,
                        "MEMBER",
                        SanctionEvents.AGGREGATE_TYPE,
                        new SanctionEvents.NotificationPayload(4L, 9L, "WARNING", null),
                        "{\"memberId\":4,\"sanctionId\":9,\"sanctionType\":\"WARNING\",\"endsAt\":null}",
                        "{\"endsAt\": null, \"memberId\": 4, \"sanctionId\": 9, \"sanctionType\": \"WARNING\"}"),
                new Wire(
                        "WITHDRAWAL_BASECAMP_CLEANUP",
                        WithdrawalEvents.BASECAMP_CLEANUP_EVENT_TYPE,
                        "MEMBER",
                        WithdrawalEvents.AGGREGATE_TYPE,
                        new WithdrawalEvents.Payload(4L),
                        "{\"memberId\":4}",
                        "{\"memberId\": 4}"),
                new Wire(
                        "WITHDRAWAL_PROGRAM_CLEANUP",
                        WithdrawalEvents.PROGRAM_CLEANUP_EVENT_TYPE,
                        "MEMBER",
                        WithdrawalEvents.AGGREGATE_TYPE,
                        new WithdrawalEvents.Payload(4L),
                        "{\"memberId\":4}",
                        "{\"memberId\": 4}"),
                new Wire(
                        "WITHDRAWAL_MEMORY_CLEANUP",
                        WithdrawalEvents.MEMORY_CLEANUP_EVENT_TYPE,
                        "MEMBER",
                        WithdrawalEvents.AGGREGATE_TYPE,
                        new WithdrawalEvents.Payload(4L),
                        "{\"memberId\":4}",
                        "{\"memberId\": 4}"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("wires")
    @DisplayName("[NFR-04] 이벤트 이름과 집합 종류는 outbox_event에 저장된 값 그대로다")
    void typeAndAggregateMatchStoredValues(Wire wire) {
        assertThat(wire.typeConstant()).isEqualTo(wire.type());
        assertThat(wire.aggregateConstant()).isEqualTo(wire.aggregate());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("wires")
    @DisplayName("[NFR-04] payload를 기록하면 필드 이름과 순서가 정해진 JSON이 된다")
    void payloadSerializesToPinnedJson(Wire wire) {
        // when
        String json = jsonMapper.writeValueAsString(wire.payload());

        // then
        assertThat(json).isEqualTo(wire.json());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("wires")
    @DisplayName("[NFR-04] DB에 저장된 모양의 payload JSON을 처리기가 읽는 타입으로 되읽을 수 있다")
    void storedJsonDeserializesToPayload(Wire wire) {
        // when
        Object payload = jsonMapper.readValue(wire.stored(), wire.payload().getClass());

        // then
        assertThat(payload).isEqualTo(wire.payload());
    }
}
