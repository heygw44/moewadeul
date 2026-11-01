package com.moewadeul.notification.application.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.moewadeul.common.outbox.OutboxMessage;
import com.moewadeul.notification.application.NotificationEventTypes;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import tools.jackson.databind.json.JsonMapper;

class NotificationEventHandlerTest {

    private static final long EVENT_ID = 77L;

    private final List<NotificationDraft> written = new ArrayList<>();
    private final List<Long> writtenEventIds = new ArrayList<>();
    private final NotificationEventHandler handler =
            new NotificationEventHandler(JsonMapper.builder().build(), new NotificationWriter(null, null, null) {
                @Override
                int write(long eventId, List<NotificationDraft> drafts) {
                    writtenEventIds.add(eventId);
                    written.addAll(drafts);
                    return drafts.size();
                }
            });

    @Test
    @DisplayName("[F-20] 알림으로 처리하는 이벤트와 메일 수신 설정 대상 이벤트는 같다")
    void handlesExactlyEmailConfigurableEvents() {
        assertThat(handler.eventTypes()).containsExactlyInAnyOrderElementsOf(NotificationEventTypes.EMAIL_CONFIGURABLE);
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource(delimiter = '|', textBlock = """
            BASECAMP_APPLIED              | {"leaderId": 3, "applicantId": 2, "applicationId": 1}
            BASECAMP_APPROVED             | {"basecampId": 30, "applicantId": 2, "applicationId": 1}
            BASECAMP_REJECTED             | {"basecampId": 30, "applicantId": 2, "applicationId": 1}
            BASECAMP_KICKED               | {"reason": "NO_CONTACT", "memberId": 4, "basecampId": 30}
            BASECAMP_MEMBER_CHANGED       | {"memberIds": [4, 5], "basecampId": 30}
            BASECAMP_CONFIRMED            | {"memberIds": [4, 5], "basecampId": 30}
            BASECAMP_CANCELED             | {"memberIds": [4, 5], "basecampId": 30}
            BASECAMP_COMPLETED            | {"memberIds": [4, 5], "basecampId": 30}
            MEMBER_REPORT_RESOLVED        | {"result": "ACTIONED", "reportId": 7, "reporterId": 8}
            SANCTION_CONFIRMED            | {"endsAt": null, "memberId": 4, "sanctionId": 9, "sanctionType": "WARNING"}
            PROGRAM_APPLICATION_CONFIRMED | {"memberId": 2, "programId": 3, "applicationId": 1}
            PROGRAM_APPLICATION_CANCELED  | {"reason": "USER", "memberId": 2, "refunded": true, "programId": 3, "applicationId": 1}
            PROGRAM_APPLICATION_EXPIRED   | {"memberId": 2, "programId": 3, "applicationId": 1}
            PROGRAM_SEAT_RELEASED         | {"memberIds": [4, 5], "programId": 3}
            """)
    @DisplayName("[F-20] 저장된 이벤트를 처리하면 그 이벤트 종류의 알림을 이벤트 ID로 저장한다")
    void writesNotificationsOfTheEventType(String eventType, String payload) {
        // when
        handler.handle(message(eventType, payload));

        // then
        assertThat(writtenEventIds).containsExactly(EVENT_ID);
        assertThat(written).isNotEmpty().extracting(NotificationDraft::type).containsOnly(eventType);
    }

    @ParameterizedTest(name = "{1}")
    @CsvSource(delimiter = '|', textBlock = """
            null          | BASECAMP_KICKED 이벤트 payload가 비어 있습니다. eventId=77
            {"memberId":  | BASECAMP_KICKED 이벤트 payload를 읽을 수 없습니다. eventId=77
            """)
    @DisplayName("[NFR-04] payload가 비었거나 읽을 수 없으면 알림을 저장하지 않고 이벤트 이름과 ID를 담아 실패한다")
    void rejectsUnreadablePayload(String payload, String error) {
        assertThatThrownBy(() -> handler.handle(message("BASECAMP_KICKED", payload)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(error);
        assertThat(writtenEventIds).isEmpty();
    }

    private static OutboxMessage message(String eventType, String payload) {
        return new OutboxMessage(EVENT_ID, eventType, "BASECAMP", 30L, payload, Instant.EPOCH);
    }
}
