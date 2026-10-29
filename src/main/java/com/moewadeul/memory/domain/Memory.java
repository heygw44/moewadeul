package com.moewadeul.memory.domain;

import com.moewadeul.common.error.BusinessException;
import com.moewadeul.common.error.CommonErrorCode;
import com.moewadeul.common.error.InvalidFieldException;
import com.moewadeul.common.moderation.PendingReview;
import com.moewadeul.common.time.KoreaTime;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "memory")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Memory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "member_id")
    private long memberId;

    @Column(name = "spot_id")
    private Long spotId;

    private double latitude;

    private double longitude;

    @Column(name = "visited_date")
    private LocalDate visitedDate;

    // 컬럼이 TINYINT라서, Hibernate가 short를 기본 SMALLINT로 보고 스키마 검증에서 어긋나지 않게 JDBC 타입을 지정한다.
    @JdbcTypeCode(SqlTypes.TINYINT)
    private short nights;

    private String content;

    @Enumerated(EnumType.STRING)
    private MemoryVisibility visibility;

    @Enumerated(EnumType.STRING)
    private MemoryStatus status;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    private Memory(long memberId, MemoryDetails details, Instant now) {
        this.memberId = memberId;
        apply(details);
        this.status = MemoryStatus.ACTIVE;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public static Memory write(long memberId, MemoryDetails details, Instant now) {
        if (details == null || now == null) {
            throw new IllegalArgumentException("기록을 만드는 데 필요한 값이 null입니다.");
        }
        requireNotFuture(details, now);
        return new Memory(memberId, details, now);
    }

    public void revise(MemoryDetails details, Instant now) {
        if (details == null || now == null) {
            throw new IllegalArgumentException("기록을 고치는 데 필요한 값이 null입니다.");
        }
        requireNotFuture(details, now);
        apply(details);
        this.updatedAt = now;
    }

    public MemoryDetails details() {
        return new MemoryDetails(spotId, latitude, longitude, visitedDate, nights, content, visibility);
    }

    public void requireWrittenBy(long memberId) {
        if (this.memberId != memberId) {
            throw new BusinessException(CommonErrorCode.ACCESS_DENIED);
        }
    }

    public boolean isPublic() {
        return visibility == MemoryVisibility.PUBLIC;
    }

    public boolean isActive() {
        return status == MemoryStatus.ACTIVE;
    }

    public boolean isVisibleToOthers() {
        return isPublic() && isActive();
    }

    public void receiveReport(long unreviewedReportCount, Instant now) {
        if (unreviewedReportCount < PendingReview.REPORT_THRESHOLD) {
            return;
        }
        if (status != MemoryStatus.ACTIVE) {
            throw new IllegalStateException("정상 기록만 검토 대기로 바꿀 수 있습니다. 현재 상태: " + status);
        }
        this.status = MemoryStatus.PENDING_REVIEW;
        this.updatedAt = now;
    }

    public void hide(Instant now) {
        if (status != MemoryStatus.ACTIVE && status != MemoryStatus.PENDING_REVIEW) {
            throw new BusinessException(MemoryErrorCode.MEMORY_INVALID_STATE);
        }
        this.status = MemoryStatus.HIDDEN;
        this.updatedAt = now;
    }

    public void restore(Instant now) {
        if (status != MemoryStatus.PENDING_REVIEW && status != MemoryStatus.HIDDEN) {
            throw new BusinessException(MemoryErrorCode.MEMORY_INVALID_STATE);
        }
        this.status = MemoryStatus.ACTIVE;
        this.updatedAt = now;
    }

    private static void requireNotFuture(MemoryDetails details, Instant now) {
        if (details.visitedDate().isAfter(KoreaTime.dateOf(now))) {
            throw new InvalidFieldException("visitedDate", "방문일은 오늘(한국 날짜)보다 뒤일 수 없어요.");
        }
    }

    private void apply(MemoryDetails details) {
        this.spotId = details.spotId();
        this.latitude = details.latitude();
        this.longitude = details.longitude();
        this.visitedDate = details.visitedDate();
        this.nights = (short) details.nights();
        this.content = details.content();
        this.visibility = details.visibility();
    }
}
