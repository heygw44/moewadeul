package com.moewadeul.program.domain;

import com.moewadeul.common.error.BusinessException;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "program")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Program {

    public static final int TITLE_MAX_LENGTH = 100;
    public static final int DESCRIPTION_MAX_LENGTH = 5000;
    public static final int LOCATION_TEXT_MAX_LENGTH = 255;
    public static final int CAPACITY_MIN = 1;
    public static final int CAPACITY_MAX = 1000;
    public static final int FEE_MIN = 0;
    public static final int FEE_MAX = 1_000_000;
    public static final int PAYMENT_DEADLINE_MINUTES_MIN = 1;
    public static final int PAYMENT_DEADLINE_MINUTES_MAX = 1440;
    public static final int DEFAULT_PAYMENT_DEADLINE_MINUTES = 15;
    public static final int MIN_TRUST_LEVEL_FOR_OVERNIGHT = 1;
    public static final int REFUND_CANCEL_HOURS_BEFORE_START = 72;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String title;

    @JdbcTypeCode(SqlTypes.LONGVARCHAR)
    private String description;

    @Column(name = "spot_id")
    private Long spotId;

    @Column(name = "location_text")
    private String locationText;

    @Column(name = "start_at")
    private Instant startAt;

    @Column(name = "end_at")
    private Instant endAt;

    private int capacity;

    private int fee;

    @Column(name = "apply_open_at")
    private Instant applyOpenAt;

    @Column(name = "apply_close_at")
    private Instant applyCloseAt;

    @JdbcTypeCode(SqlTypes.SMALLINT)
    @Column(name = "payment_deadline_minutes")
    private int paymentDeadlineMinutes;

    private boolean overnight;

    @Enumerated(EnumType.STRING)
    private ProgramStatus status;

    @Column(name = "created_by")
    private long createdBy;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    private Program(long createdBy, ProgramDetails details, Instant now) {
        this.createdBy = createdBy;
        this.title = details.title();
        this.description = details.description();
        this.spotId = details.spotId();
        this.locationText = details.locationText();
        this.startAt = details.startAt();
        this.endAt = details.endAt();
        this.capacity = details.capacity();
        this.fee = details.fee();
        this.applyOpenAt = details.applyOpenAt();
        this.applyCloseAt = details.applyCloseAt();
        this.paymentDeadlineMinutes = details.paymentDeadlineMinutes();
        this.overnight = details.overnight();
        this.status = ProgramStatus.SCHEDULED;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public static Program create(long createdBy, ProgramDetails details, Instant now) {
        if (details == null || now == null) {
            throw new IllegalArgumentException("행사를 만드는 데 필요한 값이 null입니다.");
        }
        requireValid(
                details.title(),
                details.description(),
                details.locationText(),
                details.capacity(),
                details.fee(),
                details.paymentDeadlineMinutes(),
                new Schedule(details.applyOpenAt(), details.applyCloseAt(), details.startAt(), details.endAt()));
        if (!details.startAt().isAfter(now)) {
            throw new IllegalArgumentException("행사 시작 시각은 현재보다 늦어야 합니다.");
        }
        return new Program(createdBy, details, now);
    }

    public ProgramChange revise(ProgramRevision revision, Instant now, boolean applicationsExist) {
        if (revision == null || now == null) {
            throw new IllegalArgumentException("행사를 고치는 데 필요한 값이 null입니다.");
        }
        if (status == ProgramStatus.CANCELED) {
            throw new BusinessException(ProgramErrorCode.PROGRAM_INVALID_STATE);
        }
        int newCapacity = revision.capacity() == null ? capacity : revision.capacity();
        if ((applicationsExist || !now.isBefore(applyOpenAt)) && newCapacity < capacity) {
            throw new BusinessException(ProgramErrorCode.PROGRAM_CAPACITY_DECREASE);
        }
        Program merged = merge(revision);
        requireValid(
                merged.title,
                merged.description,
                merged.locationText,
                merged.capacity,
                merged.fee,
                merged.paymentDeadlineMinutes,
                new Schedule(merged.applyOpenAt, merged.applyCloseAt, merged.startAt, merged.endAt));
        List<String> changedFields = changedFields(merged);
        int capacityBefore = capacity;
        apply(merged, now);
        return new ProgramChange(changedFields, capacityBefore, capacity);
    }

    public void cancel(Instant now) {
        if (status == ProgramStatus.CANCELED) {
            throw new BusinessException(ProgramErrorCode.PROGRAM_INVALID_STATE);
        }
        this.status = ProgramStatus.CANCELED;
        this.updatedAt = now;
    }

    public ProgramPhase phaseAt(Instant now) {
        return ProgramPhase.of(status, applyOpenAt, applyCloseAt, now);
    }

    public void requireApplicableAt(Instant now) {
        switch (phaseAt(now)) {
            case CANCELED -> throw new BusinessException(ProgramErrorCode.PROGRAM_INVALID_STATE);
            case UPCOMING, CLOSED -> throw new BusinessException(ProgramErrorCode.PROGRAM_NOT_IN_APPLY_PERIOD);
            case OPEN -> {}
        }
    }

    public boolean isTrustLevelSufficient(int trustLevel) {
        return !overnight || trustLevel >= MIN_TRUST_LEVEL_FOR_OVERNIGHT;
    }

    public Instant paymentDueAt(Instant now) {
        return now.plus(Duration.ofMinutes(paymentDeadlineMinutes));
    }

    public Instant refundDeadline() {
        return startAt.minus(Duration.ofHours(REFUND_CANCEL_HOURS_BEFORE_START));
    }

    private Program merge(ProgramRevision revision) {
        Program merged = new Program();
        merged.title = revision.title() == null ? title : revision.title();
        merged.description = revision.description() == null ? description : revision.description();
        merged.spotId = revision.spotId().present() ? revision.spotId().value() : spotId;
        merged.locationText = revision.locationText() == null ? locationText : revision.locationText();
        merged.startAt = revision.startAt() == null ? startAt : revision.startAt();
        merged.endAt = revision.endAt() == null ? endAt : revision.endAt();
        merged.capacity = revision.capacity() == null ? capacity : revision.capacity();
        merged.fee = revision.fee() == null ? fee : revision.fee();
        merged.applyOpenAt = revision.applyOpenAt() == null ? applyOpenAt : revision.applyOpenAt();
        merged.applyCloseAt = revision.applyCloseAt() == null ? applyCloseAt : revision.applyCloseAt();
        merged.paymentDeadlineMinutes =
                revision.paymentDeadlineMinutes() == null ? paymentDeadlineMinutes : revision.paymentDeadlineMinutes();
        merged.overnight = revision.overnight() == null ? overnight : revision.overnight();
        return merged;
    }

    private List<String> changedFields(Program merged) {
        List<String> names = new ArrayList<>();
        addIfChanged(names, "title", title, merged.title);
        addIfChanged(names, "description", description, merged.description);
        addIfChanged(names, "spotId", spotId, merged.spotId);
        addIfChanged(names, "locationText", locationText, merged.locationText);
        addIfChanged(names, "startAt", startAt, merged.startAt);
        addIfChanged(names, "endAt", endAt, merged.endAt);
        addIfChanged(names, "capacity", capacity, merged.capacity);
        addIfChanged(names, "fee", fee, merged.fee);
        addIfChanged(names, "applyOpenAt", applyOpenAt, merged.applyOpenAt);
        addIfChanged(names, "applyCloseAt", applyCloseAt, merged.applyCloseAt);
        addIfChanged(names, "paymentDeadlineMinutes", paymentDeadlineMinutes, merged.paymentDeadlineMinutes);
        addIfChanged(names, "overnight", overnight, merged.overnight);
        return List.copyOf(names);
    }

    private static void addIfChanged(List<String> names, String name, Object before, Object after) {
        if (!Objects.equals(before, after)) {
            names.add(name);
        }
    }

    private void apply(Program merged, Instant now) {
        this.title = merged.title;
        this.description = merged.description;
        this.spotId = merged.spotId;
        this.locationText = merged.locationText;
        this.startAt = merged.startAt;
        this.endAt = merged.endAt;
        this.capacity = merged.capacity;
        this.fee = merged.fee;
        this.applyOpenAt = merged.applyOpenAt;
        this.applyCloseAt = merged.applyCloseAt;
        this.paymentDeadlineMinutes = merged.paymentDeadlineMinutes;
        this.overnight = merged.overnight;
        this.updatedAt = now;
    }

    private record Schedule(Instant applyOpenAt, Instant applyCloseAt, Instant startAt, Instant endAt) {}

    private static void requireValid(
            String title,
            String description,
            String locationText,
            int capacity,
            int fee,
            int paymentDeadlineMinutes,
            Schedule schedule) {
        requireText(title, TITLE_MAX_LENGTH, "제목");
        requireText(description, DESCRIPTION_MAX_LENGTH, "설명");
        requireText(locationText, LOCATION_TEXT_MAX_LENGTH, "장소 설명");
        requireRange(capacity, CAPACITY_MIN, CAPACITY_MAX, "정원");
        requireRange(fee, FEE_MIN, FEE_MAX, "참가비");
        requireRange(paymentDeadlineMinutes, PAYMENT_DEADLINE_MINUTES_MIN, PAYMENT_DEADLINE_MINUTES_MAX, "결제 기한(분)");
        requireSchedule(schedule);
    }

    private static void requireText(String value, int maxLength, String label) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(label + "은(는) 비워 둘 수 없습니다.");
        }
        if (value.length() > maxLength) {
            throw new IllegalArgumentException(label + "은(는) " + maxLength + "자 이하여야 합니다.");
        }
    }

    private static void requireRange(int value, int min, int max, String label) {
        if (value < min || value > max) {
            throw new IllegalArgumentException(label + "은(는) " + min + "~" + max + " 범위여야 합니다.");
        }
    }

    private static void requireSchedule(Schedule schedule) {
        if (schedule.applyOpenAt() == null
                || schedule.applyCloseAt() == null
                || schedule.startAt() == null
                || schedule.endAt() == null) {
            throw new IllegalArgumentException("신청 시작·마감 시각과 행사 시작·종료 시각은 null일 수 없습니다.");
        }
        if (!schedule.applyOpenAt().isBefore(schedule.applyCloseAt())) {
            throw new IllegalArgumentException("신청 마감 시각은 신청 시작 시각보다 늦어야 합니다.");
        }
        if (schedule.applyCloseAt().isAfter(schedule.startAt())) {
            throw new IllegalArgumentException("신청 마감 시각은 행사 시작 시각보다 늦을 수 없습니다.");
        }
        if (!schedule.startAt().isBefore(schedule.endAt())) {
            throw new IllegalArgumentException("행사 종료 시각은 행사 시작 시각보다 늦어야 합니다.");
        }
    }
}
