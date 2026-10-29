package com.moewadeul.trust.domain.report;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.moewadeul.common.error.BusinessException;
import com.moewadeul.common.error.CommonErrorCode;
import com.moewadeul.common.event.TrustEvents;
import com.moewadeul.common.outbox.OutboxEventDraft;
import com.moewadeul.trust.domain.TrustErrorCode;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class MemberReportTest {

    private static final long REPORT_ID = 21L;
    private static final Instant NOW = Instant.parse("2026-10-05T03:00:00Z");

    @Test
    @DisplayName("[SN-01] 회원 신고를 만들면 접수 상태이고, 종류는 유형에서 정해지며 후기 ID는 없다")
    void receiveMemberReport() {
        MemberReport report = MemberReport.receive(10L, 11L, 3L, null, ReportType.NO_SHOW, "약속 장소에 오지 않았다", NOW);

        assertThat(report.getReporterId()).isEqualTo(10L);
        assertThat(report.getTargetMemberId()).isEqualTo(11L);
        assertThat(report.getBasecampId()).isEqualTo(3L);
        assertThat(report.getKind()).isEqualTo(ReportKind.MEMBER);
        assertThat(report.getCompanionReviewId()).isNull();
        assertThat(report.getStatus()).isEqualTo(ReportStatus.RECEIVED);
        assertThat(report.isUrgent()).isFalse();
        assertThat(report.getCreatedAt()).isEqualTo(NOW);
        assertThat(report.getUpdatedAt()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("[SN-05] 성희롱·위협 신고는 긴급 신고로 만들어진다")
    void harassmentReportIsUrgent() {
        MemberReport report = MemberReport.receive(10L, 11L, 3L, null, ReportType.HARASSMENT_OR_THREAT, "위협했다", NOW);

        assertThat(report.isUrgent()).isTrue();
    }

    @Test
    @DisplayName("[SN-07] 후기 신고는 REVIEW 종류이고 동행 후기 ID를 담는다")
    void receiveReviewReport() {
        MemberReport report = MemberReport.receive(10L, 11L, 3L, 77L, ReportType.INAPPROPRIATE_REVIEW, "허위 후기", NOW);

        assertThat(report.getKind()).isEqualTo(ReportKind.REVIEW);
        assertThat(report.getCompanionReviewId()).isEqualTo(77L);
    }

    @Test
    @DisplayName("후기 신고에 후기 ID가 없거나 회원 신고에 후기 ID가 있으면 INVALID_INPUT이다")
    void reviewIdMustMatchKind() {
        assertThatThrownBy(() -> MemberReport.receive(10L, 11L, 3L, null, ReportType.INAPPROPRIATE_REVIEW, "내용", NOW))
                .isInstanceOfSatisfying(BusinessException.class, e -> {
                    assertThat(e.getErrorCode()).isEqualTo(CommonErrorCode.INVALID_INPUT);
                    assertThat(e.getMessage()).isEqualTo("후기를 신고하려면 동행 후기 ID가 필요해요.");
                });
        assertThatThrownBy(() -> MemberReport.receive(10L, 11L, 3L, 77L, ReportType.NO_SHOW, "내용", NOW))
                .isInstanceOfSatisfying(BusinessException.class, e -> {
                    assertThat(e.getErrorCode()).isEqualTo(CommonErrorCode.INVALID_INPUT);
                    assertThat(e.getMessage()).isEqualTo("회원 신고에는 동행 후기 ID를 넣을 수 없어요.");
                });
    }

    @Test
    @DisplayName("자기 자신을 신고하면 REPORT_NOT_ELIGIBLE이고, 후기 ID가 맞지 않으면 자기 신고보다 먼저 걸린다")
    void rejectsSelfReport() {
        assertThatThrownBy(() -> MemberReport.receive(10L, 10L, 3L, null, ReportType.NO_SHOW, "내용", NOW))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(TrustErrorCode.REPORT_NOT_ELIGIBLE));
        assertThatThrownBy(() -> MemberReport.receive(10L, 10L, 3L, 77L, ReportType.NO_SHOW, "내용", NOW))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(CommonErrorCode.INVALID_INPUT));
    }

    @Test
    @DisplayName("내용이 비었거나 1000자를 넘거나 유형이 null이면 만들지 못한다")
    void rejectsInvalidValues() {
        assertThatThrownBy(() -> MemberReport.receive(10L, 11L, 3L, null, ReportType.NO_SHOW, " ", NOW))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> MemberReport.receive(10L, 11L, 3L, null, ReportType.NO_SHOW, "가".repeat(1001), NOW))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> MemberReport.receive(10L, 11L, 3L, null, null, "내용", NOW))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(MemberReport.receive(10L, 11L, 3L, null, ReportType.NO_SHOW, "가".repeat(1000), NOW))
                .isNotNull();
    }

    @Test
    @DisplayName("[SN-04] 접수된 신고의 검토를 시작하면 검토 중이 되고 검토한 관리자와 시각을 남긴다")
    void startReviewMovesReceivedToInReview() {
        MemberReport report = receivedReport();
        Instant later = NOW.plusSeconds(60);

        report.startReview(99L, later);

        assertThat(report.getStatus()).isEqualTo(ReportStatus.IN_REVIEW);
        assertThat(report.getHandledBy()).isEqualTo(99L);
        assertThat(report.getHandledAt()).isNull();
        assertThat(report.getUpdatedAt()).isEqualTo(later);
    }

    @Test
    @DisplayName("[SN-04] 검토 중인 신고를 조치하면 ACTIONED가 되고 처리한 관리자, 처리 시각, 메모를 남긴다")
    void actionRecordsHandlerAndNote() {
        MemberReport report = inReviewReport();
        Instant later = NOW.plusSeconds(120);

        report.action(98L, "경고 처리", later);

        assertThat(report.getStatus()).isEqualTo(ReportStatus.ACTIONED);
        assertThat(report.getHandledBy()).isEqualTo(98L);
        assertThat(report.getHandledAt()).isEqualTo(later);
        assertThat(report.getResultNote()).isEqualTo("경고 처리");
        assertThat(report.getUpdatedAt()).isEqualTo(later);
    }

    @Test
    @DisplayName("[SN-05] 검토 중인 신고를 기각하면 DISMISSED가 되고, 공백뿐인 메모는 없는 것으로 저장한다")
    void dismissStoresBlankNoteAsNull() {
        MemberReport report = inReviewReport();

        report.dismiss(98L, "  ", NOW.plusSeconds(120));

        assertThat(report.getStatus()).isEqualTo(ReportStatus.DISMISSED);
        assertThat(report.getResultNote()).isNull();
        assertThat(report.getHandledAt()).isEqualTo(NOW.plusSeconds(120));
    }

    @Test
    @DisplayName("[SN-04] 접수 상태에서 바로 조치하거나 기각하면 REPORT_INVALID_STATE이다")
    void cannotResolveBeforeReview() {
        MemberReport report = receivedReport();

        assertThatThrownBy(() -> report.action(99L, null, NOW))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(TrustErrorCode.REPORT_INVALID_STATE));
        assertThatThrownBy(() -> report.dismiss(99L, null, NOW))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(TrustErrorCode.REPORT_INVALID_STATE));
        assertThat(report.getStatus()).isEqualTo(ReportStatus.RECEIVED);
    }

    @Test
    @DisplayName("[SN-04] 이미 검토 중이거나 처리된 신고는 다시 검토하거나 처리하지 못한다")
    void cannotRepeatTransitions() {
        MemberReport inReview = inReviewReport();
        MemberReport actioned = inReviewReport();
        actioned.action(99L, null, NOW);
        MemberReport dismissed = inReviewReport();
        dismissed.dismiss(99L, null, NOW);

        assertThatThrownBy(() -> inReview.startReview(99L, NOW)).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> actioned.action(99L, null, NOW)).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> actioned.dismiss(99L, null, NOW)).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> dismissed.action(99L, null, NOW)).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> dismissed.startReview(99L, NOW)).isInstanceOf(BusinessException.class);
        assertThatCode(inReview::requireInReview).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("처리 메모가 1000자를 넘거나 처리 시각이 null이면 거부한다")
    void rejectsInvalidResolution() {
        MemberReport report = inReviewReport();

        assertThatThrownBy(() -> report.action(99L, "가".repeat(1001), NOW))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> report.dismiss(99L, null, null)).isInstanceOf(IllegalArgumentException.class);
        report.dismiss(99L, "가".repeat(1000), NOW);
        assertThat(report.getResultNote()).hasSize(1000);
    }

    @Test
    @DisplayName("[SN-04][SN-05] 조치하면 ACTIONED, 기각하면 DISMISSED 결과로 MEMBER_REPORT_RESOLVED 이벤트를 남긴다")
    void resolveRaisesResolvedEvent() {
        MemberReport actioned = inReviewReport();
        MemberReport dismissed = inReviewReport();

        actioned.action(98L, null, NOW);
        dismissed.dismiss(98L, null, NOW);

        assertThat(actioned.pullEvents()).containsExactly(resolved("ACTIONED"));
        assertThat(dismissed.pullEvents()).containsExactly(resolved("DISMISSED"));
    }

    @Test
    @DisplayName("처리가 실패하면 이벤트를 남기지 않고, 이벤트를 꺼내면 대기열이 비어 있다")
    void failedResolveRaisesNothingAndPullEmptiesQueue() {
        MemberReport received = receivedReport();
        MemberReport inReview = inReviewReport();

        assertThatThrownBy(() -> received.action(99L, null, NOW)).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> inReview.dismiss(99L, "가".repeat(1001), NOW))
                .isInstanceOf(IllegalArgumentException.class);
        inReview.action(99L, null, NOW);

        assertThat(received.pullEvents()).isEmpty();
        assertThat(inReview.pullEvents()).hasSize(1);
        assertThat(inReview.pullEvents()).isEmpty();
    }

    private static OutboxEventDraft resolved(String result) {
        return new OutboxEventDraft(
                TrustEvents.REPORT_RESOLVED,
                TrustEvents.REPORT_AGGREGATE_TYPE,
                REPORT_ID,
                new TrustEvents.ReportResolvedPayload(REPORT_ID, 10L, result));
    }

    private static MemberReport receivedReport() {
        MemberReport report = MemberReport.receive(10L, 11L, 3L, null, ReportType.NO_SHOW, "약속 장소에 오지 않았다", NOW);
        ReflectionTestUtils.setField(report, "id", REPORT_ID);
        return report;
    }

    private static MemberReport inReviewReport() {
        MemberReport report = receivedReport();
        report.startReview(99L, NOW);
        return report;
    }
}
