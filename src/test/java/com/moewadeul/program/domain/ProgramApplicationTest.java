package com.moewadeul.program.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.moewadeul.common.error.BusinessException;
import com.moewadeul.common.error.CommonErrorCode;
import com.moewadeul.common.event.ProgramEvents;
import com.moewadeul.common.outbox.OutboxEventDraft;
import com.moewadeul.common.testsupport.MutableClock;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.BeanUtils;
import org.springframework.test.util.ReflectionTestUtils;

class ProgramApplicationTest {

    private static final long APPLICATION_ID = 31L;
    private static final long MEMBER_ID = 7L;
    private static final long PROGRAM_ID = 5L;
    private static final Instant NOW = MutableClock.DEFAULT_INSTANT;
    private static final Instant DUE = NOW.plus(Duration.ofMinutes(15));
    private static final Instant REFUND_DEADLINE = NOW.plus(Duration.ofDays(4));

    @Test
    @DisplayName("[F-16][SN-14] 결제 대기 신청은 제재로 취소되면 사유 SANCTIONED와 취소 시각이 기록된다")
    void cancelsPendingBySanction() {
        ProgramApplication application = application(ProgramApplicationStatus.PENDING_PAYMENT);
        Instant cancelAt = NOW.plus(Duration.ofMinutes(3));

        application.cancelByMemberRemoval(ProgramCancelReason.SANCTIONED, cancelAt);

        assertThat(application.getStatus()).isEqualTo(ProgramApplicationStatus.CANCELED);
        assertThat(application.getCancelReason()).isEqualTo(ProgramCancelReason.SANCTIONED);
        assertThat(application.getCanceledAt()).isEqualTo(cancelAt);
        assertThat(application.getUpdatedAt()).isEqualTo(cancelAt);
    }

    @Test
    @DisplayName("[PV-02] 결제 대기 신청은 탈퇴로 취소되면 사유 WITHDRAWN이 기록되고, 제재나 탈퇴가 아닌 사유는 받지 않는다")
    void cancelsPendingByWithdrawal() {
        ProgramApplication application = application(ProgramApplicationStatus.PENDING_PAYMENT);

        assertThatThrownBy(() -> application.cancelByMemberRemoval(ProgramCancelReason.USER, NOW))
                .isInstanceOf(IllegalArgumentException.class);
        application.cancelByMemberRemoval(ProgramCancelReason.WITHDRAWN, NOW);

        assertThat(application.getStatus()).isEqualTo(ProgramApplicationStatus.CANCELED);
        assertThat(application.getCancelReason()).isEqualTo(ProgramCancelReason.WITHDRAWN);
        assertThat(application.getCanceledAt()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("[F-16][SN-14] 결제 대기가 아닌 신청을 제재로 취소하면 PROGRAM_INVALID_STATE이고 신청은 바뀌지 않는다")
    void rejectsSanctionCancelOutsidePending() {
        for (ProgramApplicationStatus status : new ProgramApplicationStatus[] {
            ProgramApplicationStatus.CONFIRMED, ProgramApplicationStatus.CANCELED, ProgramApplicationStatus.EXPIRED
        }) {
            ProgramApplication application = application(status);

            assertThatThrownBy(() -> application.cancelByMemberRemoval(ProgramCancelReason.SANCTIONED, NOW))
                    .isInstanceOfSatisfying(
                            BusinessException.class,
                            e -> assertThat(e.getErrorCode()).isEqualTo(ProgramErrorCode.PROGRAM_INVALID_STATE));
            assertThat(application.getStatus()).isEqualTo(status);
            assertThat(application.getCancelReason()).isNull();
        }
    }

    @Test
    @DisplayName("[F-19][PG-04] 결제 기한 직전에 결제하면 확정되고 확정 시각이 기록된다")
    void confirmsJustBeforeDue() {
        ProgramApplication application = application(ProgramApplicationStatus.PENDING_PAYMENT);
        Instant payAt = DUE.minusNanos(1_000);

        application.confirmPayment(payAt);

        assertThat(application.getStatus()).isEqualTo(ProgramApplicationStatus.CONFIRMED);
        assertThat(application.getConfirmedAt()).isEqualTo(payAt);
        assertThat(application.getUpdatedAt()).isEqualTo(payAt);
    }

    @Test
    @DisplayName("[F-19][PG-04] 결제 기한 정각에 결제하면 PROGRAM_PAYMENT_EXPIRED이고 신청은 바뀌지 않는다")
    void rejectsAtDueInstant() {
        ProgramApplication application = application(ProgramApplicationStatus.PENDING_PAYMENT);

        assertThatThrownBy(() -> application.confirmPayment(DUE))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ProgramErrorCode.PROGRAM_PAYMENT_EXPIRED));
        assertThat(application.getStatus()).isEqualTo(ProgramApplicationStatus.PENDING_PAYMENT);
        assertThat(application.getConfirmedAt()).isNull();
    }

    @Test
    @DisplayName("[F-19] 만료된 신청은 PROGRAM_PAYMENT_EXPIRED, 확정·취소된 신청은 PROGRAM_INVALID_STATE로 결제를 거부한다")
    void rejectsPaymentByState() {
        assertConfirmFails(ProgramApplicationStatus.EXPIRED, ProgramErrorCode.PROGRAM_PAYMENT_EXPIRED);
        assertConfirmFails(ProgramApplicationStatus.CONFIRMED, ProgramErrorCode.PROGRAM_INVALID_STATE);
        assertConfirmFails(ProgramApplicationStatus.CANCELED, ProgramErrorCode.PROGRAM_INVALID_STATE);
    }

    @Test
    @DisplayName("[F-19][PG-06] 결제 대기 신청은 결제 기한이 지났어도 취소할 수 있고 환불 대상이 아니다")
    void cancelsPendingEvenAfterDue() {
        ProgramApplication application = application(ProgramApplicationStatus.PENDING_PAYMENT);
        Instant cancelAt = DUE.plus(Duration.ofMinutes(5));

        boolean refund = application.cancelByMember(cancelAt, REFUND_DEADLINE);

        assertThat(refund).isFalse();
        assertThat(application.getStatus()).isEqualTo(ProgramApplicationStatus.CANCELED);
        assertThat(application.getCancelReason()).isEqualTo(ProgramCancelReason.USER);
        assertThat(application.getCanceledAt()).isEqualTo(cancelAt);
    }

    @Test
    @DisplayName("[F-19][PG-06] 확정 신청은 환불 기한 정각까지 취소할 수 있고 환불 대상이다")
    void cancelsConfirmedAtRefundDeadline() {
        ProgramApplication application = application(ProgramApplicationStatus.CONFIRMED);

        boolean refund = application.cancelByMember(REFUND_DEADLINE, REFUND_DEADLINE);

        assertThat(refund).isTrue();
        assertThat(application.getStatus()).isEqualTo(ProgramApplicationStatus.CANCELED);
        assertThat(application.getCancelReason()).isEqualTo(ProgramCancelReason.USER);
    }

    @Test
    @DisplayName("[F-19][PG-06] 확정 신청을 환불 기한 1초 뒤에 취소하면 PROGRAM_CANCEL_NOT_ALLOWED이고 신청은 바뀌지 않는다")
    void rejectsConfirmedCancelAfterRefundDeadline() {
        ProgramApplication application = application(ProgramApplicationStatus.CONFIRMED);

        assertThatThrownBy(() -> application.cancelByMember(REFUND_DEADLINE.plusSeconds(1), REFUND_DEADLINE))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ProgramErrorCode.PROGRAM_CANCEL_NOT_ALLOWED));
        assertThat(application.getStatus()).isEqualTo(ProgramApplicationStatus.CONFIRMED);
        assertThat(application.getCancelReason()).isNull();
    }

    @Test
    @DisplayName("[F-19] 이미 취소되거나 만료된 신청을 취소하면 PROGRAM_INVALID_STATE이다")
    void rejectsCancelOfFinishedApplication() {
        for (ProgramApplicationStatus status :
                new ProgramApplicationStatus[] {ProgramApplicationStatus.CANCELED, ProgramApplicationStatus.EXPIRED}) {
            ProgramApplication application = application(status);

            assertThatThrownBy(() -> application.cancelByMember(NOW, REFUND_DEADLINE))
                    .isInstanceOfSatisfying(
                            BusinessException.class,
                            e -> assertThat(e.getErrorCode()).isEqualTo(ProgramErrorCode.PROGRAM_INVALID_STATE));
            assertThat(application.getStatus()).isEqualTo(status);
        }
    }

    private static void assertConfirmFails(ProgramApplicationStatus status, ProgramErrorCode expected) {
        ProgramApplication application = application(status);

        assertThatThrownBy(() -> application.confirmPayment(NOW))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(expected));
        assertThat(application.getStatus()).isEqualTo(status);
    }

    @Test
    @DisplayName("[F-16] 신청한 회원이 아니면 requireAppliedBy가 ACCESS_DENIED다")
    void requireAppliedByRejectsOthers() {
        ProgramApplication application = application(ProgramApplicationStatus.PENDING_PAYMENT);

        assertThatCode(() -> application.requireAppliedBy(7L)).doesNotThrowAnyException();
        assertThatThrownBy(() -> application.requireAppliedBy(8L))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(CommonErrorCode.ACCESS_DENIED));
    }

    @Test
    @DisplayName("[F-19] 결제를 확정하면 PROGRAM_APPLICATION_CONFIRMED 이벤트를 남긴다")
    void confirmPaymentRaisesConfirmedEvent() {
        ProgramApplication application = application(ProgramApplicationStatus.PENDING_PAYMENT);

        application.confirmPayment(NOW);

        assertThat(application.pullEvents()).containsExactly(draft(ProgramEvents.APPLICATION_CONFIRMED, confirmed()));
    }

    @Test
    @DisplayName("[F-19][PG-06] 결제 대기 신청을 취소하면 환불 없음으로, 확정 신청을 취소하면 환불로 PROGRAM_APPLICATION_CANCELED 이벤트를 남긴다")
    void cancelByMemberRaisesCanceledEventWithRefundFlag() {
        ProgramApplication pending = application(ProgramApplicationStatus.PENDING_PAYMENT);
        ProgramApplication confirmed = application(ProgramApplicationStatus.CONFIRMED);

        pending.cancelByMember(NOW, REFUND_DEADLINE);
        confirmed.cancelByMember(NOW, REFUND_DEADLINE);

        assertThat(pending.pullEvents())
                .containsExactly(draft(ProgramEvents.APPLICATION_CANCELED, canceled("USER", false)));
        assertThat(confirmed.pullEvents())
                .containsExactly(draft(ProgramEvents.APPLICATION_CANCELED, canceled("USER", true)));
    }

    @Test
    @DisplayName("[SN-14] 제재로 취소하면 취소 이벤트를 남기고, 탈퇴로 취소하면 알리지 않으므로 아무것도 남기지 않는다")
    void cancelByMemberRemovalNotifiesOnlySanctioned() {
        ProgramApplication sanctioned = application(ProgramApplicationStatus.PENDING_PAYMENT);
        ProgramApplication withdrawn = application(ProgramApplicationStatus.PENDING_PAYMENT);

        sanctioned.cancelByMemberRemoval(ProgramCancelReason.SANCTIONED, NOW);
        withdrawn.cancelByMemberRemoval(ProgramCancelReason.WITHDRAWN, NOW);

        assertThat(sanctioned.pullEvents())
                .containsExactly(draft(ProgramEvents.APPLICATION_CANCELED, canceled("SANCTIONED", false)));
        assertThat(withdrawn.pullEvents()).isEmpty();
    }

    @Test
    @DisplayName("전이가 실패하면 이벤트를 남기지 않는다")
    void failedTransitionsRaiseNothing() {
        ProgramApplication expired = application(ProgramApplicationStatus.EXPIRED);
        ProgramApplication canceled = application(ProgramApplicationStatus.CANCELED);
        ProgramApplication lateConfirmed = application(ProgramApplicationStatus.CONFIRMED);

        assertThatThrownBy(() -> expired.confirmPayment(NOW)).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> canceled.cancelByMember(NOW, REFUND_DEADLINE)).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> lateConfirmed.cancelByMember(REFUND_DEADLINE.plusSeconds(1), REFUND_DEADLINE))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> lateConfirmed.cancelByMemberRemoval(ProgramCancelReason.SANCTIONED, NOW))
                .isInstanceOf(BusinessException.class);

        assertThat(expired.pullEvents()).isEmpty();
        assertThat(canceled.pullEvents()).isEmpty();
        assertThat(lateConfirmed.pullEvents()).isEmpty();
    }

    @Test
    @DisplayName("이벤트를 꺼내면 대기열이 비어서 두 번째로 꺼낼 때는 아무것도 나오지 않는다")
    void pullEventsEmptiesQueue() {
        ProgramApplication application = application(ProgramApplicationStatus.PENDING_PAYMENT);
        application.confirmPayment(NOW);

        assertThat(application.pullEvents()).hasSize(1);
        assertThat(application.pullEvents()).isEmpty();
    }

    private static ProgramEvents.ApplicationPayload confirmed() {
        return new ProgramEvents.ApplicationPayload(APPLICATION_ID, MEMBER_ID, PROGRAM_ID);
    }

    private static ProgramEvents.CanceledPayload canceled(String reason, boolean refunded) {
        return new ProgramEvents.CanceledPayload(APPLICATION_ID, MEMBER_ID, PROGRAM_ID, reason, refunded);
    }

    private static OutboxEventDraft draft(String eventType, Object payload) {
        return new OutboxEventDraft(eventType, ProgramEvents.APPLICATION_AGGREGATE_TYPE, APPLICATION_ID, payload);
    }

    private static ProgramApplication application(ProgramApplicationStatus status) {
        ProgramApplication application = BeanUtils.instantiateClass(ProgramApplication.class);
        ReflectionTestUtils.setField(application, "id", APPLICATION_ID);
        ReflectionTestUtils.setField(application, "memberId", MEMBER_ID);
        ReflectionTestUtils.setField(application, "programId", PROGRAM_ID);
        ReflectionTestUtils.setField(application, "status", status);
        ReflectionTestUtils.setField(application, "paymentDueAt", DUE);
        return application;
    }
}
