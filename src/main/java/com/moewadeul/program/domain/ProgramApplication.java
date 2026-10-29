package com.moewadeul.program.domain;

import com.moewadeul.common.error.BusinessException;
import com.moewadeul.common.error.CommonErrorCode;
import com.moewadeul.common.event.ProgramEvents;
import com.moewadeul.common.outbox.OutboxEventDraft;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "program_application")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProgramApplication {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "program_id")
    private long programId;

    @Column(name = "member_id")
    private long memberId;

    @Enumerated(EnumType.STRING)
    private ProgramApplicationStatus status;

    @Column(name = "payment_due_at")
    private Instant paymentDueAt;

    @Column(name = "confirmed_at")
    private Instant confirmedAt;

    @Column(name = "canceled_at")
    private Instant canceledAt;

    @Column(name = "cancel_reason")
    @Enumerated(EnumType.STRING)
    private ProgramCancelReason cancelReason;

    @Column(name = "review_requested_at")
    private Instant reviewRequestedAt;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @Transient
    @Getter(AccessLevel.NONE)
    private final List<Supplier<OutboxEventDraft>> deferredEvents = new ArrayList<>();

    public boolean isSeatHolderAt(Instant now) {
        return status == ProgramApplicationStatus.CONFIRMED
                || (status == ProgramApplicationStatus.PENDING_PAYMENT && now.isBefore(paymentDueAt));
    }

    public void confirmPayment(Instant now) {
        boolean dueReached = status == ProgramApplicationStatus.PENDING_PAYMENT && !now.isBefore(paymentDueAt);
        if (status == ProgramApplicationStatus.EXPIRED || dueReached) {
            throw new BusinessException(ProgramErrorCode.PROGRAM_PAYMENT_EXPIRED);
        }
        if (status != ProgramApplicationStatus.PENDING_PAYMENT) {
            throw new BusinessException(ProgramErrorCode.PROGRAM_INVALID_STATE);
        }
        this.status = ProgramApplicationStatus.CONFIRMED;
        this.confirmedAt = now;
        this.updatedAt = now;
        long memberId = this.memberId;
        long programId = this.programId;
        raise(ProgramEvents.APPLICATION_CONFIRMED, () -> new ProgramEvents.ApplicationPayload(id, memberId, programId));
    }

    public boolean cancelByMember(Instant now, Instant refundDeadline) {
        boolean wasConfirmed;
        switch (status) {
            case PENDING_PAYMENT -> wasConfirmed = false;
            case CONFIRMED -> {
                if (now.isAfter(refundDeadline)) {
                    throw new BusinessException(ProgramErrorCode.PROGRAM_CANCEL_NOT_ALLOWED);
                }
                wasConfirmed = true;
            }
            case CANCELED, EXPIRED -> throw new BusinessException(ProgramErrorCode.PROGRAM_INVALID_STATE);
            default -> throw new IllegalStateException("알 수 없는 신청 상태입니다: " + status);
        }
        this.status = ProgramApplicationStatus.CANCELED;
        this.cancelReason = ProgramCancelReason.USER;
        this.canceledAt = now;
        this.updatedAt = now;
        raiseCanceled(ProgramCancelReason.USER, wasConfirmed);
        return wasConfirmed;
    }

    public void requireAppliedBy(long memberId) {
        if (this.memberId != memberId) {
            throw new BusinessException(CommonErrorCode.ACCESS_DENIED);
        }
    }

    public void cancelByMemberRemoval(ProgramCancelReason reason, Instant now) {
        if (reason != ProgramCancelReason.SANCTIONED && reason != ProgramCancelReason.WITHDRAWN) {
            throw new IllegalArgumentException("제재나 탈퇴가 아닌 취소 이유입니다: " + reason);
        }
        if (status != ProgramApplicationStatus.PENDING_PAYMENT) {
            throw new BusinessException(ProgramErrorCode.PROGRAM_INVALID_STATE);
        }
        this.status = ProgramApplicationStatus.CANCELED;
        this.cancelReason = reason;
        this.canceledAt = now;
        this.updatedAt = now;
        if (reason == ProgramCancelReason.SANCTIONED) {
            raiseCanceled(reason, false);
        }
    }

    public List<OutboxEventDraft> pullEvents() {
        List<OutboxEventDraft> events =
                deferredEvents.stream().map(Supplier::get).toList();
        deferredEvents.clear();
        return events;
    }

    private void raiseCanceled(ProgramCancelReason reason, boolean refunded) {
        long memberId = this.memberId;
        long programId = this.programId;
        String reasonName = reason.name();
        raise(
                ProgramEvents.APPLICATION_CANCELED,
                () -> new ProgramEvents.CanceledPayload(id, memberId, programId, reasonName, refunded));
    }

    private void raise(String eventType, Supplier<Object> payload) {
        deferredEvents.add(
                () -> new OutboxEventDraft(eventType, ProgramEvents.APPLICATION_AGGREGATE_TYPE, id, payload.get()));
    }
}
