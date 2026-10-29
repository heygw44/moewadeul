package com.moewadeul.trust.domain.report;

import com.moewadeul.common.error.BusinessException;
import com.moewadeul.common.error.CommonErrorCode;
import com.moewadeul.trust.domain.TrustErrorCode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "member_report")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MemberReport {

    public static final int CONTENT_MAX_LENGTH = 1000;

    public static final int RESULT_NOTE_MAX_LENGTH = 1000;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "reporter_id")
    private long reporterId;

    @Column(name = "target_member_id")
    private long targetMemberId;

    @Column(name = "basecamp_id")
    private long basecampId;

    @Enumerated(EnumType.STRING)
    private ReportKind kind;

    @Column(name = "companion_review_id")
    private Long companionReviewId;

    @Enumerated(EnumType.STRING)
    private ReportType type;

    private String content;

    private boolean urgent;

    @Enumerated(EnumType.STRING)
    private ReportStatus status;

    @Column(name = "handled_by")
    private Long handledBy;

    @Column(name = "handled_at")
    private Instant handledAt;

    @Column(name = "result_note")
    private String resultNote;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    private MemberReport(
            long reporterId,
            long targetMemberId,
            long basecampId,
            Long companionReviewId,
            ReportType type,
            String content,
            Instant now) {
        this.reporterId = reporterId;
        this.targetMemberId = targetMemberId;
        this.basecampId = basecampId;
        this.kind = type.kind();
        this.companionReviewId = companionReviewId;
        this.type = type;
        this.content = content;
        this.urgent = type.isUrgent();
        this.status = ReportStatus.RECEIVED;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public static MemberReport receive(
            long reporterId,
            long targetMemberId,
            long basecampId,
            Long companionReviewId,
            ReportType type,
            String content,
            Instant now) {
        if (type == null || now == null) {
            throw new IllegalArgumentException("신고 유형이나 접수 시각이 null입니다.");
        }
        boolean isReviewReport = type.kind() == ReportKind.REVIEW;
        if (isReviewReport && companionReviewId == null) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT, "후기를 신고하려면 동행 후기 ID가 필요해요.");
        }
        if (!isReviewReport && companionReviewId != null) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT, "회원 신고에는 동행 후기 ID를 넣을 수 없어요.");
        }
        if (reporterId == targetMemberId) {
            throw new BusinessException(TrustErrorCode.REPORT_NOT_ELIGIBLE);
        }
        if (content == null || content.isBlank() || content.length() > CONTENT_MAX_LENGTH) {
            throw new IllegalArgumentException("신고 내용은 1~" + CONTENT_MAX_LENGTH + "자여야 합니다.");
        }
        return new MemberReport(reporterId, targetMemberId, basecampId, companionReviewId, type, content, now);
    }

    public void startReview(long adminId, Instant now) {
        requireNow(now);
        requireStatus(ReportStatus.RECEIVED);
        this.status = ReportStatus.IN_REVIEW;
        this.handledBy = adminId;
        this.updatedAt = now;
    }

    public void action(long adminId, String note, Instant now) {
        resolve(ReportStatus.ACTIONED, adminId, note, now);
    }

    public void dismiss(long adminId, String note, Instant now) {
        resolve(ReportStatus.DISMISSED, adminId, note, now);
    }

    public void requireInReview() {
        requireStatus(ReportStatus.IN_REVIEW);
    }

    private void resolve(ReportStatus result, long adminId, String note, Instant now) {
        requireNow(now);
        if (note != null && note.length() > RESULT_NOTE_MAX_LENGTH) {
            throw new IllegalArgumentException("처리 메모는 " + RESULT_NOTE_MAX_LENGTH + "자 이하여야 합니다.");
        }
        requireStatus(ReportStatus.IN_REVIEW);
        this.status = result;
        this.handledBy = adminId;
        this.handledAt = now;
        this.resultNote = note == null || note.isBlank() ? null : note;
        this.updatedAt = now;
    }

    private void requireStatus(ReportStatus expected) {
        if (status != expected) {
            throw new BusinessException(TrustErrorCode.REPORT_INVALID_STATE);
        }
    }

    private static void requireNow(Instant now) {
        if (now == null) {
            throw new IllegalArgumentException("처리 시각이 null입니다.");
        }
    }
}
