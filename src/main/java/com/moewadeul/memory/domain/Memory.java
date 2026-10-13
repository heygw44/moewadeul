package com.moewadeul.memory.domain;

import com.moewadeul.common.error.BusinessException;
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

/**
 * 회원이 다녀온 곳을 사진과 글로 남긴 추억 기록. 사진은 {@link MemoryImage}가 기록 ID를 가리키는 방식으로 붙는다.
 *
 * <p>작성자와 장소는 다른 모듈의 엔티티라서 ID로만 가리킨다. 작성자만 기록을 고치고 지울 수 있고, 공개 범위도 언제든 바꿀 수 있다. 비공개 기록은
 * 작성자만 본다. 내용 규칙은 {@link MemoryDetails}가 가진다.
 *
 * <p>상태({@link MemoryStatus})는 신고와 관리자 조치로만 바뀌고, 작성자가 내용이나 공개 범위를 고쳐도 그대로다. 그래서 숨긴 기록을 작성자가
 * 다시 공개해도 관리자가 복구하기 전에는 다른 사람에게 보이지 않는다.
 */
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

    /** 호출하면 memberId인 회원의 기록을 details 내용으로 만든다. details나 now가 null이면 {@link IllegalArgumentException}을 던진다. */
    public static Memory write(long memberId, MemoryDetails details, Instant now) {
        if (details == null || now == null) {
            throw new IllegalArgumentException("기록을 만드는 데 필요한 값이 null입니다.");
        }
        return new Memory(memberId, details, now);
    }

    /** 호출하면 기록 내용을 details로 통째로 바꾼다. details나 now가 null이면 {@link IllegalArgumentException}을 던진다. */
    public void revise(MemoryDetails details, Instant now) {
        if (details == null || now == null) {
            throw new IllegalArgumentException("기록을 고치는 데 필요한 값이 null입니다.");
        }
        apply(details);
        this.updatedAt = now;
    }

    /** 호출하면 지금 기록 내용을 돌려준다. 수정 요청에 없는 값은 이 값을 그대로 쓴다. */
    public MemoryDetails details() {
        return new MemoryDetails(spotId, latitude, longitude, visitedDate, nights, content, visibility);
    }

    /** memberId인 회원이 쓴 기록이면 true다. */
    public boolean isWrittenBy(long memberId) {
        return this.memberId == memberId;
    }

    /** 공개 범위가 PUBLIC이면 true다. */
    public boolean isPublic() {
        return visibility == MemoryVisibility.PUBLIC;
    }

    /** 상태가 ACTIVE이면 true다. */
    public boolean isActive() {
        return status == MemoryStatus.ACTIVE;
    }

    /** 공개이면서 ACTIVE라서 작성자가 아닌 사람도 볼 수 있으면 true다. */
    public boolean isVisibleToOthers() {
        return isPublic() && isActive();
    }

    /**
     * 호출하면 검토 전 신고가 쌓인 기록을 {@link MemoryStatus#PENDING_REVIEW}로 바꾸고 수정 시각을 now로 갱신한다. ACTIVE가 아니면
     * {@link IllegalStateException}을 던진다. 신고하는 쪽이 ACTIVE인 기록만 신고받으므로 이 예외는 호출하는 쪽의 실수를 뜻한다.
     */
    public void markPendingReview(Instant now) {
        if (status != MemoryStatus.ACTIVE) {
            throw new IllegalStateException("정상 기록만 검토 대기로 바꿀 수 있습니다. 현재 상태: " + status);
        }
        this.status = MemoryStatus.PENDING_REVIEW;
        this.updatedAt = now;
    }

    /**
     * 호출하면 관리자가 기록을 숨긴 것으로 상태를 {@link MemoryStatus#HIDDEN}으로 바꾸고 수정 시각을 now로 갱신한다. ACTIVE나 PENDING_REVIEW에서만
     * 숨길 수 있고, 이미 숨겼으면 {@link BusinessException}(MEMORY_INVALID_STATE)을 던진다.
     */
    public void hide(Instant now) {
        if (status != MemoryStatus.ACTIVE && status != MemoryStatus.PENDING_REVIEW) {
            throw new BusinessException(MemoryErrorCode.MEMORY_INVALID_STATE);
        }
        this.status = MemoryStatus.HIDDEN;
        this.updatedAt = now;
    }

    /**
     * 호출하면 관리자가 기록을 되살린 것으로 상태를 {@link MemoryStatus#ACTIVE}로 바꾸고 수정 시각을 now로 갱신한다. PENDING_REVIEW나 HIDDEN에서만
     * 되살릴 수 있고, 이미 ACTIVE이면 {@link BusinessException}(MEMORY_INVALID_STATE)을 던진다.
     */
    public void restore(Instant now) {
        if (status != MemoryStatus.PENDING_REVIEW && status != MemoryStatus.HIDDEN) {
            throw new BusinessException(MemoryErrorCode.MEMORY_INVALID_STATE);
        }
        this.status = MemoryStatus.ACTIVE;
        this.updatedAt = now;
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
