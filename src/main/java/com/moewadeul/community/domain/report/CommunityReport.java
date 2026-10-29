package com.moewadeul.community.domain.report;

import com.moewadeul.common.moderation.ContentReportReason;
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
@Table(name = "community_report")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CommunityReport {

    public static final int CONTENT_MAX_LENGTH = 1000;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_type")
    private CommunityReportTargetType targetType;

    @Column(name = "target_id")
    private long targetId;

    @Column(name = "reporter_id")
    private long reporterId;

    @Enumerated(EnumType.STRING)
    private ContentReportReason reason;

    private String content;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    @Column(name = "created_at")
    private Instant createdAt;

    private CommunityReport(
            CommunityReportTargetType targetType,
            long targetId,
            long reporterId,
            ContentReportReason reason,
            String content,
            Instant now) {
        this.targetType = targetType;
        this.targetId = targetId;
        this.reporterId = reporterId;
        this.reason = reason;
        this.content = content;
        this.createdAt = now;
    }

    public static CommunityReport of(
            CommunityReportTargetType targetType,
            long targetId,
            long reporterId,
            ContentReportReason reason,
            String content,
            Instant now) {
        if (targetType == null || reason == null || now == null) {
            throw new IllegalArgumentException("신고를 만드는 데 필요한 값이 null입니다.");
        }
        if (content != null && content.length() > CONTENT_MAX_LENGTH) {
            throw new IllegalArgumentException("신고 내용은 " + CONTENT_MAX_LENGTH + "자 이하여야 합니다.");
        }
        return new CommunityReport(targetType, targetId, reporterId, reason, content, now);
    }
}
