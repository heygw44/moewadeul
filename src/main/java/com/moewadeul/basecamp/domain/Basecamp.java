package com.moewadeul.basecamp.domain;

import com.moewadeul.common.error.BusinessException;
import com.moewadeul.common.error.CommonErrorCode;
import com.moewadeul.common.event.BasecampEvents;
import com.moewadeul.common.outbox.OutboxEventDraft;
import com.moewadeul.common.time.KoreaTime;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "basecamp")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Basecamp {

    public static final int TITLE_MAX_LENGTH = 100;
    public static final int DESCRIPTION_MAX_LENGTH = 2000;
    public static final int CONTACT_INFO_MAX_LENGTH = 255;

    public static final int MIN_CONFIRM_HEADCOUNT = 2;

    public static final int MAX_PENDING_APPLICATIONS = 20;

    public static final Duration EARLY_LEAVE_WINDOW = Duration.ofHours(48);

    public static final Duration CONTACT_VISIBLE_AFTER_COMPLETION = Duration.ofDays(7);

    private static final Set<BasecampStatus> RECRUITING_ONLY = Set.of(BasecampStatus.RECRUITING);
    private static final Set<BasecampStatus> BEFORE_CONFIRM = Set.of(BasecampStatus.RECRUITING, BasecampStatus.CLOSED);
    private static final Set<BasecampStatus> NOT_FINISHED =
            Set.of(BasecampStatus.RECRUITING, BasecampStatus.CLOSED, BasecampStatus.CONFIRMED);

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "leader_id")
    private long leaderId;

    @Column(name = "spot_id")
    private long spotId;

    private String title;

    private String description;

    @Column(name = "start_date")
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    @Embedded
    private Capacity capacity;

    @Enumerated(EnumType.STRING)
    private BasecampStatus status;

    @Column(name = "closed_reason")
    @Enumerated(EnumType.STRING)
    private ClosedReason closedReason;

    @Embedded
    private JoinCondition joinCondition;

    @Column(name = "contact_info")
    private String contactInfo;

    @Column(name = "confirmed_at")
    private Instant confirmedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "canceled_at")
    private Instant canceledAt;

    @Column(name = "cancel_reason")
    @Enumerated(EnumType.STRING)
    private CancelReason cancelReason;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @Getter(AccessLevel.NONE)
    @OneToMany(
            mappedBy = "basecamp",
            cascade = {CascadeType.PERSIST, CascadeType.MERGE})
    private List<BasecampMember> members = new ArrayList<>();

    @Getter(AccessLevel.NONE)
    @OneToMany(
            mappedBy = "basecamp",
            cascade = {CascadeType.PERSIST, CascadeType.MERGE})
    private List<BasecampApplication> applications = new ArrayList<>();

    @Transient
    @Getter(AccessLevel.NONE)
    private final List<Supplier<OutboxEventDraft>> deferredEvents = new ArrayList<>();

    private Basecamp(long leaderId, long spotId, BasecampDetails details, Instant now) {
        this.leaderId = leaderId;
        this.spotId = spotId;
        this.title = details.title();
        this.description = details.description();
        this.startDate = details.startDate();
        this.endDate = details.endDate();
        this.capacity = details.capacity();
        this.joinCondition = details.joinCondition();
        this.status = BasecampStatus.RECRUITING;
        this.createdAt = now;
        this.updatedAt = now;
        this.members.add(BasecampMember.leader(this, leaderId, now));
    }

    public static Basecamp open(long leaderId, long spotId, BasecampDetails details, Instant now) {
        if (details == null || now == null) {
            throw new IllegalArgumentException("베이스캠프를 만드는 데 필요한 값이 null입니다.");
        }
        BasecampOpenPolicy.requireSchedule(details.startDate(), details.endDate(), KoreaTime.dateOf(now));
        requireValidText(details.title(), details.description());
        if (details.capacity() == null || details.joinCondition() == null) {
            throw new IllegalArgumentException("정원과 합류 조건은 null일 수 없습니다.");
        }
        return new Basecamp(leaderId, spotId, details, now);
    }

    public int headcount() {
        return (int) members.stream().filter(BasecampMember::isActive).count();
    }

    public List<Long> activeMemberIds() {
        return members.stream()
                .filter(BasecampMember::isActive)
                .map(BasecampMember::getMemberId)
                .toList();
    }

    public List<OutboxEventDraft> pullEvents() {
        List<OutboxEventDraft> events =
                deferredEvents.stream().map(Supplier::get).toList();
        deferredEvents.clear();
        return events;
    }

    public boolean isFull() {
        return capacity.isFilledBy(headcount());
    }

    public boolean isLeader(long memberId) {
        return leaderId == memberId;
    }

    public List<BasecampMember> getMembers() {
        return Collections.unmodifiableList(members);
    }

    public List<BasecampApplication> getApplications() {
        return Collections.unmodifiableList(applications);
    }

    public BasecampRelation relationOf(Long memberId) {
        if (memberId == null) {
            return BasecampRelation.NONE;
        }
        Optional<BasecampMember> activeMember = findMember(memberId).filter(BasecampMember::isActive);
        if (activeMember.isPresent()) {
            return activeMember.get().isLeader() ? BasecampRelation.LEADER : BasecampRelation.MEMBER;
        }
        boolean pending = findApplicationOf(memberId)
                .filter(BasecampApplication::isPending)
                .isPresent();
        return pending ? BasecampRelation.APPLICANT : BasecampRelation.NONE;
    }

    public boolean canViewContact(long memberId, Instant now) {
        if (!isActiveMember(memberId)) {
            return false;
        }
        return switch (status) {
            case RECRUITING, CLOSED -> isLeader(memberId);
            case CONFIRMED -> true;
            case COMPLETED -> !now.isAfter(completedAt.plus(CONTACT_VISIBLE_AFTER_COMPLETION));
            default -> false;
        };
    }

    public void close(Instant now) {
        requireStatus(RECRUITING_ONLY);
        markClosed(ClosedReason.LEADER, now);
    }

    public void reopen(Instant now) {
        requireStatus(Set.of(BasecampStatus.CLOSED));
        if (isFull()) {
            throw new BusinessException(BasecampErrorCode.BASECAMP_FULL);
        }
        markRecruiting(now);
    }

    public void confirm(Instant now) {
        requireStatus(BEFORE_CONFIRM);
        if (headcount() < MIN_CONFIRM_HEADCOUNT) {
            throw new BusinessException(BasecampErrorCode.BASECAMP_NOT_ENOUGH_MEMBERS);
        }
        markConfirmed(now);
    }

    public void cancel(CancelReason reason, Instant now) {
        if (reason == null) {
            throw new IllegalArgumentException("취소 사유가 null입니다.");
        }
        requireStatus(NOT_FINISHED);
        if (!KoreaTime.dateOf(now).isBefore(startDate)) {
            throw new BusinessException(BasecampErrorCode.BASECAMP_INVALID_STATE);
        }
        markCanceled(reason, now);
    }

    public void checkApplicable(long applicantId) {
        requireStatus(RECRUITING_ONLY);
        requireNotBlockedByMembership(applicantId);
        findApplicationOf(applicantId).ifPresent(Basecamp::requireResubmittable);
    }

    public BasecampApplication apply(long applicantId, String message, Instant now) {
        checkApplicable(applicantId);
        requireUnderPendingLimit();
        Optional<BasecampApplication> existing = findApplicationOf(applicantId);
        if (existing.isPresent()) {
            existing.get().resubmit(message, now);
            raiseApplied(existing.get(), applicantId);
            return existing.get();
        }
        BasecampApplication application = BasecampApplication.submit(this, applicantId, message, now);
        applications.add(application);
        raiseApplied(application, applicantId);
        return application;
    }

    public void cancelApplication(long applicantId, Instant now) {
        requireStatus(BEFORE_CONFIRM);
        BasecampApplication application = findApplicationOf(applicantId).orElseThrow(Basecamp::notFound);
        requirePending(application);
        application.cancel(now);
    }

    public BasecampApplication checkApprovable(long applicationId) {
        BasecampApplication application = requireApplication(applicationId);
        requireStatus(RECRUITING_ONLY);
        requirePending(application);
        if (isFull()) {
            throw new BusinessException(BasecampErrorCode.BASECAMP_FULL);
        }
        return application;
    }

    public BasecampApplication approve(long applicationId, Instant now) {
        BasecampApplication application = checkApprovable(applicationId);
        application.approve(now);
        members.add(BasecampMember.member(this, application.getApplicantId(), now));
        closeIfFull(now);
        raiseDecision(BasecampEvents.APPROVED, application);
        return application;
    }

    public BasecampApplication reject(long applicationId, Instant now) {
        BasecampApplication application = requireApplication(applicationId);
        requireStatus(RECRUITING_ONLY);
        requirePending(application);
        application.reject(now);
        raiseDecision(BasecampEvents.REJECTED, application);
        return application;
    }

    public void leave(long memberId, Instant now) {
        requireStatus(NOT_FINISHED);
        BasecampMember member = requireActiveMember(memberId);
        if (member.isLeader()) {
            throw new BusinessException(BasecampErrorCode.BASECAMP_LEADER_CANNOT_LEAVE);
        }
        member.leave(isEarlyLeave(now), now);
        reopenIfAutoClosed(now);
    }

    public MemberRemoval removeMember(long memberId, RemovalCause cause, Instant now) {
        if (cause == null) {
            throw new IllegalArgumentException("정리 이유가 null입니다.");
        }
        if (!NOT_FINISHED.contains(status)) {
            return MemberRemoval.NOTHING;
        }
        if (isLeader(memberId)) {
            markCanceled(
                    cause == RemovalCause.SANCTION ? CancelReason.LEADER_SANCTIONED : CancelReason.LEADER_WITHDRAWN,
                    now);
            return MemberRemoval.LEADER_CANCELED;
        }
        Optional<BasecampMember> activeMember = findMember(memberId).filter(BasecampMember::isActive);
        if (activeMember.isPresent()) {
            activeMember.get().removeByCleanup(now);
            reopenIfAutoClosed(now);
            raise(BasecampEvents.MEMBER_CHANGED, () -> new BasecampEvents.MembersPayload(id, activeMemberIds()));
            return MemberRemoval.MEMBER_LEFT;
        }
        Optional<BasecampApplication> pending = findApplicationOf(memberId).filter(BasecampApplication::isPending);
        if (pending.isPresent()) {
            pending.get().cancel(now);
            touch(now);
            return MemberRemoval.APPLICATION_CANCELED;
        }
        return MemberRemoval.NOTHING;
    }

    public void kick(long memberId, KickReason reason, Instant now) {
        if (reason == null) {
            throw new IllegalArgumentException("강퇴 사유가 null입니다.");
        }
        requireStatus(BEFORE_CONFIRM);
        BasecampMember member = requireActiveMember(memberId);
        if (member.isLeader()) {
            throw new BusinessException(BasecampErrorCode.BASECAMP_LEADER_CANNOT_LEAVE);
        }
        member.kick(reason, now);
        reopenIfAutoClosed(now);
        raise(BasecampEvents.KICKED, () -> new BasecampEvents.KickedPayload(id, memberId, reason.name()));
    }

    public void revise(BasecampRevision revision, Instant now) {
        requireStatus(BEFORE_CONFIRM);
        requireValidText(revision.title(), revision.description());
        if (revision.capacity() == null || revision.joinCondition() == null) {
            throw new IllegalArgumentException("정원과 합류 조건은 null일 수 없습니다.");
        }
        if (revision.capacity().isLessThan(capacity)) {
            throw new BusinessException(BasecampErrorCode.BASECAMP_CAPACITY_INVALID);
        }
        this.title = revision.title();
        this.description = revision.description();
        this.capacity = revision.capacity();
        this.joinCondition = revision.joinCondition();
        touch(now);
        reopenIfAutoClosed(now);
    }

    public void registerContact(String contactInfo, Instant now) {
        if (contactInfo == null || contactInfo.isBlank() || contactInfo.length() > CONTACT_INFO_MAX_LENGTH) {
            throw new IllegalArgumentException("연락 수단은 공백뿐이 아닌 " + CONTACT_INFO_MAX_LENGTH + "자 이하여야 합니다.");
        }
        requireStatus(NOT_FINISHED);
        this.contactInfo = contactInfo;
        touch(now);
    }

    private void markClosed(ClosedReason reason, Instant now) {
        this.status = BasecampStatus.CLOSED;
        this.closedReason = reason;
        touch(now);
    }

    private void markRecruiting(Instant now) {
        this.status = BasecampStatus.RECRUITING;
        this.closedReason = null;
        touch(now);
    }

    private void markConfirmed(Instant now) {
        this.status = BasecampStatus.CONFIRMED;
        this.closedReason = null;
        this.confirmedAt = now;
        expirePendingApplications(now);
        touch(now);
        raise(BasecampEvents.CONFIRMED, () -> new BasecampEvents.MembersPayload(id, activeMemberIds()));
    }

    private void markCanceled(CancelReason reason, Instant now) {
        this.status = BasecampStatus.CANCELED;
        this.closedReason = null;
        this.canceledAt = now;
        this.cancelReason = reason;
        expirePendingApplications(now);
        touch(now);
        raise(BasecampEvents.CANCELED, () -> new BasecampEvents.MembersPayload(id, activeMembersExceptLeader()));
    }

    private void raise(String eventType, Supplier<Object> payload) {
        deferredEvents.add(() -> new OutboxEventDraft(eventType, BasecampEvents.AGGREGATE_TYPE, id, payload.get()));
    }

    private void raiseApplied(BasecampApplication application, long applicantId) {
        raise(
                BasecampEvents.APPLIED,
                () -> new BasecampEvents.AppliedPayload(application.getId(), applicantId, leaderId));
    }

    private void raiseDecision(String eventType, BasecampApplication application) {
        raise(
                eventType,
                () -> new BasecampEvents.DecisionPayload(application.getId(), application.getApplicantId(), id));
    }

    private List<Long> activeMembersExceptLeader() {
        return activeMemberIds().stream()
                .filter(memberId -> !isLeader(memberId))
                .toList();
    }

    private void closeIfFull(Instant now) {
        if (isFull()) {
            markClosed(ClosedReason.AUTO_FULL, now);
            return;
        }
        touch(now);
    }

    private void reopenIfAutoClosed(Instant now) {
        boolean autoClosed = status == BasecampStatus.CLOSED && closedReason == ClosedReason.AUTO_FULL;
        if (autoClosed && !isFull()) {
            markRecruiting(now);
            return;
        }
        touch(now);
    }

    private void expirePendingApplications(Instant now) {
        applications.stream().filter(BasecampApplication::isPending).forEach(application -> application.expire(now));
    }

    private static void requireResubmittable(BasecampApplication existing) {
        switch (existing.getStatus()) {
            case CANCELED -> {}
            case REJECTED -> throw new BusinessException(BasecampErrorCode.BASECAMP_REAPPLY_NOT_ALLOWED);
            case PENDING, APPROVED -> throw new BusinessException(BasecampErrorCode.BASECAMP_ALREADY_APPLIED);
            case EXPIRED -> throw new BusinessException(BasecampErrorCode.BASECAMP_INVALID_STATE);
        }
    }

    private void requireUnderPendingLimit() {
        long pending =
                applications.stream().filter(BasecampApplication::isPending).count();
        if (pending >= MAX_PENDING_APPLICATIONS) {
            throw new BusinessException(BasecampErrorCode.BASECAMP_PENDING_LIMIT);
        }
    }

    private void requireNotBlockedByMembership(long applicantId) {
        Optional<BasecampMember> member = findMember(applicantId);
        if (member.isEmpty()) {
            return;
        }
        if (member.get().isActive()) {
            throw new BusinessException(BasecampErrorCode.BASECAMP_ALREADY_APPLIED);
        }
        throw new BusinessException(BasecampErrorCode.BASECAMP_REAPPLY_NOT_ALLOWED);
    }

    private boolean isEarlyLeave(Instant now) {
        if (status != BasecampStatus.CONFIRMED) {
            return false;
        }
        Instant departure = startDate.atStartOfDay(KoreaTime.ZONE).toInstant();
        return !now.isBefore(departure.minus(EARLY_LEAVE_WINDOW));
    }

    private boolean isActiveMember(long memberId) {
        return findMember(memberId).filter(BasecampMember::isActive).isPresent();
    }

    private Optional<BasecampMember> findMember(long memberId) {
        return members.stream()
                .filter(member -> member.getMemberId() == memberId)
                .findFirst();
    }

    private BasecampMember requireActiveMember(long memberId) {
        return findMember(memberId).filter(BasecampMember::isActive).orElseThrow(Basecamp::notFound);
    }

    private Optional<BasecampApplication> findApplicationOf(long applicantId) {
        return applications.stream()
                .filter(application -> application.isFrom(applicantId))
                .findFirst();
    }

    private BasecampApplication requireApplication(long applicationId) {
        return applications.stream()
                .filter(application -> Objects.equals(application.getId(), applicationId))
                .findFirst()
                .orElseThrow(Basecamp::notFound);
    }

    private static void requirePending(BasecampApplication application) {
        if (!application.isPending()) {
            throw new BusinessException(BasecampErrorCode.BASECAMP_INVALID_STATE);
        }
    }

    private void requireStatus(Set<BasecampStatus> allowed) {
        if (!allowed.contains(status)) {
            throw new BusinessException(BasecampErrorCode.BASECAMP_INVALID_STATE);
        }
    }

    private static BusinessException notFound() {
        return new BusinessException(CommonErrorCode.NOT_FOUND);
    }

    private void touch(Instant now) {
        this.updatedAt = now;
    }

    private static void requireValidText(String title, String description) {
        if (title == null || title.isBlank() || title.length() > TITLE_MAX_LENGTH) {
            throw new IllegalArgumentException("제목은 공백뿐이 아닌 " + TITLE_MAX_LENGTH + "자 이하여야 합니다.");
        }
        if (description == null || description.isBlank() || description.length() > DESCRIPTION_MAX_LENGTH) {
            throw new IllegalArgumentException("설명은 공백뿐이 아닌 " + DESCRIPTION_MAX_LENGTH + "자 이하여야 합니다.");
        }
    }
}
