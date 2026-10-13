package com.moewadeul.memory.domain.report;

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

/**
 * 회원이 공개 추억 기록을 신고한 기록. 한 회원은 같은 기록을 한 번만 신고할 수 있다.
 *
 * <p>신고는 추가만 하고 고치지 않는다. 기록을 지우면 DB가 그 기록의 신고도 함께 지운다.
 */
@Entity
@Table(name = "memory_report")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MemoryReport {

    /** 신고 내용의 최대 길이다. DB 컬럼 크기와 같다. */
    public static final int CONTENT_MAX_LENGTH = 1000;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "memory_id")
    private long memoryId;

    @Column(name = "reporter_id")
    private long reporterId;

    @Enumerated(EnumType.STRING)
    private MemoryReportReason reason;

    private String content;

    // 관리자가 기록을 복구하면서 검토를 마친 것으로 표시한 시각이다. null이면 아직 검토 전이다.
    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    @Column(name = "created_at")
    private Instant createdAt;

    private MemoryReport(long memoryId, long reporterId, MemoryReportReason reason, String content, Instant now) {
        this.memoryId = memoryId;
        this.reporterId = reporterId;
        this.reason = reason;
        this.content = content;
        this.createdAt = now;
    }

    /**
     * 호출하면 reporterId인 회원이 memoryId인 기록을 reason으로 신고한 기록을 만든다. content는 없어도 된다.
     *
     * <p>reason이나 now가 null이거나 content가 {@value #CONTENT_MAX_LENGTH}자를 넘으면 {@link IllegalArgumentException}을 던진다.
     */
    public static MemoryReport of(
            long memoryId, long reporterId, MemoryReportReason reason, String content, Instant now) {
        if (reason == null || now == null) {
            throw new IllegalArgumentException("신고를 만드는 데 필요한 값이 null입니다.");
        }
        if (content != null && content.length() > CONTENT_MAX_LENGTH) {
            throw new IllegalArgumentException("신고 내용은 " + CONTENT_MAX_LENGTH + "자 이하여야 합니다.");
        }
        return new MemoryReport(memoryId, reporterId, reason, content, now);
    }
}
