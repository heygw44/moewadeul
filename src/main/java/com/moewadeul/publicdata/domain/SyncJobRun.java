package com.moewadeul.publicdata.domain;

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
import java.util.Optional;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "sync_job_run")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SyncJobRun {

    public static final int ERROR_MESSAGE_MAX_LENGTH = 2000;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "job_type")
    private SyncJobType jobType;

    @Enumerated(EnumType.STRING)
    private SyncJobStatus status;

    @Column(name = "progress_cursor")
    private String progressCursor;

    @Column(name = "processed_count")
    private int processedCount;

    @Column(name = "skipped_count")
    private int skippedCount;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    @Column(name = "error_message")
    private String errorMessage;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    private SyncJobRun(SyncJobType jobType, String startCursor, Instant now) {
        this.jobType = jobType;
        this.status = SyncJobStatus.RUNNING;
        this.progressCursor = startCursor;
        this.processedCount = 0;
        this.skippedCount = 0;
        this.startedAt = now;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public static SyncJobRun start(SyncJobType jobType, String startCursor, Instant now) {
        if (jobType == null || now == null) {
            throw new IllegalArgumentException("실행 기록을 만드는 데 필요한 값이 null입니다.");
        }
        return new SyncJobRun(jobType, startCursor, now);
    }

    public void recordProgress(String cursor, int processedDelta, int skippedDelta, Instant now) {
        requireRunning();
        requireNow(now);
        if (processedDelta < 0) {
            throw new IllegalArgumentException("처리 건수 증가분은 0 이상이어야 합니다.");
        }
        if (skippedDelta < 0) {
            throw new IllegalArgumentException("건너뛴 건수 증가분은 0 이상이어야 합니다.");
        }
        this.progressCursor = cursor;
        this.processedCount += processedDelta;
        this.skippedCount += skippedDelta;
        this.updatedAt = now;
    }

    public void complete(Instant now) {
        requireRunning();
        requireNow(now);
        this.status = SyncJobStatus.COMPLETED;
        this.finishedAt = now;
        this.updatedAt = now;
    }

    public void fail(String message, Instant now) {
        requireRunning();
        requireNow(now);
        this.status = SyncJobStatus.FAILED;
        this.errorMessage = truncate(message);
        this.finishedAt = now;
        this.updatedAt = now;
    }

    public boolean isStale(Instant now, Duration staleAfter) {
        return isRunning() && updatedAt.plus(staleAfter).isBefore(now);
    }

    public Optional<ResumePoint> resumePoint() {
        if (!isFailed() || progressCursor == null) {
            return Optional.empty();
        }
        return Optional.of(new ResumePoint(progressCursor, !madeProgress()));
    }

    private boolean madeProgress() {
        return processedCount > 0;
    }

    public boolean isRunning() {
        return status == SyncJobStatus.RUNNING;
    }

    public boolean isFailed() {
        return status == SyncJobStatus.FAILED;
    }

    private void requireRunning() {
        if (!isRunning()) {
            throw new IllegalStateException("실행 중인 기록만 바꿀 수 있습니다. status=" + status);
        }
    }

    private static void requireNow(Instant now) {
        if (now == null) {
            throw new IllegalArgumentException("시각이 null입니다.");
        }
    }

    // 자를 위치가 서로게이트 쌍의 가운데이면 깨진 글자가 남으므로 한 칸 앞에서 자른다.
    private static String truncate(String message) {
        if (message == null || message.length() <= ERROR_MESSAGE_MAX_LENGTH) {
            return message;
        }
        int end = ERROR_MESSAGE_MAX_LENGTH;
        if (Character.isHighSurrogate(message.charAt(end - 1))) {
            end--;
        }
        return message.substring(0, end);
    }
}
