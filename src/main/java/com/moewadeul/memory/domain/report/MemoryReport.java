package com.moewadeul.memory.domain.report;

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
@Table(name = "memory_report")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MemoryReport {

    public static final int CONTENT_MAX_LENGTH = 1000;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "memory_id")
    private long memoryId;

    @Column(name = "reporter_id")
    private long reporterId;

    @Enumerated(EnumType.STRING)
    private ContentReportReason reason;

    private String content;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    @Column(name = "created_at")
    private Instant createdAt;

    private MemoryReport(long memoryId, long reporterId, ContentReportReason reason, String content, Instant now) {
        this.memoryId = memoryId;
        this.reporterId = reporterId;
        this.reason = reason;
        this.content = content;
        this.createdAt = now;
    }

    public static MemoryReport of(
            long memoryId, long reporterId, ContentReportReason reason, String content, Instant now) {
        if (reason == null || now == null) {
            throw new IllegalArgumentException("신고를 만드는 데 필요한 값이 null입니다.");
        }
        if (content != null && content.length() > CONTENT_MAX_LENGTH) {
            throw new IllegalArgumentException("신고 내용은 " + CONTENT_MAX_LENGTH + "자 이하여야 합니다.");
        }
        return new MemoryReport(memoryId, reporterId, reason, content, now);
    }
}
