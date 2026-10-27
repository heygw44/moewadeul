package com.moewadeul.spot.domain.bakji;

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
@Table(name = "bakji_report")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BakjiReport {

    public static final int CONTENT_MAX_LENGTH = 1000;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "spot_id")
    private long spotId;

    @Column(name = "reporter_id")
    private long reporterId;

    @Enumerated(EnumType.STRING)
    private BakjiReportReason reason;

    private String content;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    private BakjiReport(long spotId, long reporterId, BakjiReportReason reason, String content, Instant now) {
        this.spotId = spotId;
        this.reporterId = reporterId;
        this.reason = reason;
        this.content = content;
        this.createdAt = now;
    }

    public static BakjiReport of(long spotId, long reporterId, BakjiReportReason reason, String content, Instant now) {
        if (reason == null || now == null) {
            throw new IllegalArgumentException("박지 신고를 만드는 데 필요한 값이 null입니다.");
        }
        if (content != null && content.length() > CONTENT_MAX_LENGTH) {
            throw new IllegalArgumentException("신고 내용은 " + CONTENT_MAX_LENGTH + "자 이하여야 합니다.");
        }
        return new BakjiReport(spotId, reporterId, reason, content, now);
    }
}
