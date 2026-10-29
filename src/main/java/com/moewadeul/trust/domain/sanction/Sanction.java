package com.moewadeul.trust.domain.sanction;

import com.moewadeul.common.error.BusinessException;
import com.moewadeul.trust.domain.TrustErrorCode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Duration;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "sanction")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Sanction {

    public static final Duration TEMPORARY_SUSPENSION = Duration.ofHours(72);

    public static final int REASON_MAX_LENGTH = 500;

    static final String TEMPORARY_REASON = "성희롱·위협 신고가 접수되어 관리자가 확인하기 전까지 이용을 임시로 정지해요.";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "member_id")
    private long memberId;

    @Enumerated(EnumType.STRING)
    private SanctionType type;

    private Byte level;

    @Column(name = "report_id")
    private Long reportId;

    private String reason;

    @Column(name = "starts_at")
    private Instant startsAt;

    @Column(name = "ends_at")
    private Instant endsAt;

    @Enumerated(EnumType.STRING)
    private SanctionStatus status;

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "lifted_by")
    private Long liftedBy;

    @Column(name = "lifted_at")
    private Instant liftedAt;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    private Sanction(long memberId, long reportId, Instant startsAt) {
        this.memberId = memberId;
        this.type = SanctionType.TEMPORARY_72H;
        this.reportId = reportId;
        this.reason = TEMPORARY_REASON;
        this.startsAt = startsAt;
        this.endsAt = startsAt.plus(TEMPORARY_SUSPENSION);
        this.status = SanctionStatus.ACTIVE;
        this.createdAt = startsAt;
        this.updatedAt = startsAt;
    }

    public static Sanction temporary(long memberId, long reportId, Instant startsAt) {
        if (startsAt == null) {
            throw new IllegalArgumentException("정지를 시작하는 시각이 null입니다.");
        }
        return new Sanction(memberId, reportId, startsAt);
    }

    private Sanction(
            long memberId,
            Long reportId,
            SanctionType type,
            int level,
            String reason,
            Instant startsAt,
            long createdBy) {
        this.memberId = memberId;
        this.type = type;
        this.level = (byte) level;
        this.reportId = reportId;
        this.reason = reason;
        this.startsAt = startsAt;
        this.endsAt = endsAtOf(type, startsAt);
        this.status = SanctionStatus.ACTIVE;
        this.createdBy = createdBy;
        this.createdAt = startsAt;
        this.updatedAt = startsAt;
    }

    public static Sanction confirm(
            long memberId,
            Long reportId,
            SanctionType type,
            int level,
            String reason,
            Instant startsAt,
            long createdBy) {
        if (startsAt == null) {
            throw new IllegalArgumentException("제재를 시작하는 시각이 null입니다.");
        }
        if (type == null || type == SanctionType.TEMPORARY_72H) {
            throw new IllegalArgumentException("확정 제재의 종류가 올바르지 않습니다. type=" + type);
        }
        if (level != SanctionPolicy.levelOf(type)) {
            throw new IllegalArgumentException("제재 단계가 종류와 맞지 않습니다. type=" + type + ", level=" + level);
        }
        if (reason == null || reason.isBlank() || reason.length() > REASON_MAX_LENGTH) {
            throw new IllegalArgumentException("제재 사유는 공백뿐이 아닌 " + REASON_MAX_LENGTH + "자 이하여야 합니다.");
        }
        return new Sanction(memberId, reportId, type, level, reason, startsAt, createdBy);
    }

    public void lift(long adminId, SanctionLiftReason reason, Instant now) {
        requireNow(now);
        if (reason == null) {
            throw new IllegalArgumentException("제재를 해제하는 사유가 null입니다.");
        }
        if (status != SanctionStatus.ACTIVE) {
            throw new BusinessException(TrustErrorCode.SANCTION_INVALID_STATE);
        }
        markLifted(adminId, reason.resultingStatus(), now);
    }

    public void liftForDismissedReport(long adminId, Instant now) {
        requireNow(now);
        if (type != SanctionType.TEMPORARY_72H || status == SanctionStatus.REVOKED) {
            throw new BusinessException(TrustErrorCode.SANCTION_INVALID_STATE);
        }
        markLifted(adminId, SanctionStatus.REVOKED, now);
    }

    public boolean countsAsHistory() {
        return type != SanctionType.TEMPORARY_72H && status != SanctionStatus.REVOKED;
    }

    public boolean countsForCiRetention() {
        return status != SanctionStatus.REVOKED;
    }

    private static void requireNow(Instant now) {
        if (now == null) {
            throw new IllegalArgumentException("제재를 해제하는 시각이 null입니다.");
        }
    }

    private void markLifted(long adminId, SanctionStatus liftedStatus, Instant now) {
        this.status = liftedStatus;
        this.liftedBy = adminId;
        this.liftedAt = now;
        this.updatedAt = now;
    }

    private static Instant endsAtOf(SanctionType type, Instant startsAt) {
        return switch (type) {
            case SUSPEND_7D -> startsAt.plus(Duration.ofDays(7));
            case SUSPEND_30D -> startsAt.plus(Duration.ofDays(30));
            case TEMPORARY_72H -> startsAt.plus(TEMPORARY_SUSPENSION);
            case WARNING, PERMANENT -> null;
        };
    }
}
