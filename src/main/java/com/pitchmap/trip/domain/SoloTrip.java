package com.pitchmap.trip.domain;

import com.pitchmap.common.error.BusinessException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 회원이 베이스캠프 없이 혼자 가는 야영 일정. 날짜는 한국 날짜이고, 종료일은 집으로 돌아가는 날이라 그 밤은 일정에 들지 않는다.
 *
 * <p>일정 규칙은 베이스캠프 열기와 같다. 출발일은 등록한 날의 다음 날부터 60일 안이고, 일정은 1~3박이다. 한 회원은 종료일이 지나지 않은 일정을 3개까지
 * 가질 수 있고, 그 일정끼리 밤이 겹치면 안 된다. 장소와 회원은 다른 모듈의 엔티티라서 ID로만 가리킨다. 수정은 없고 등록과 삭제만 한다.
 */
@Entity
@Table(name = "solo_trip")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SoloTrip {

    /** 출발일은 오늘로부터 이 일수 안이어야 한다. */
    public static final int MAX_DAYS_UNTIL_START = 60;

    public static final int MIN_NIGHTS = 1;
    public static final int MAX_NIGHTS = 3;

    /** 한 회원이 가질 수 있는, 종료일이 지나지 않은 일정의 최대 개수다. */
    public static final int MAX_UPCOMING_PER_MEMBER = 3;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "member_id")
    private long memberId;

    @Column(name = "spot_id")
    private long spotId;

    @Column(name = "start_date")
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    @Column(name = "created_at")
    private Instant createdAt;

    private SoloTrip(long memberId, long spotId, LocalDate startDate, LocalDate endDate, Instant now) {
        this.memberId = memberId;
        this.spotId = spotId;
        this.startDate = startDate;
        this.endDate = endDate;
        this.createdAt = now;
    }

    /**
     * 호출하면 memberId인 회원이 spotId인 장소에 가는 일정을 만든다.
     *
     * <p>출발일이 today의 다음 날부터 {@value #MAX_DAYS_UNTIL_START}일 뒤까지가 아니거나, 박 수가 {@value #MIN_NIGHTS}~{@value
     * #MAX_NIGHTS}박이 아니거나, 날짜가 null이면 SOLO_TRIP_SCHEDULE_INVALID로 거부한다. upcoming은 이 회원의 종료일이 지나지 않은
     * 일정이다. 이미 {@value #MAX_UPCOMING_PER_MEMBER}개면 SOLO_TRIP_LIMIT로, 그중 하나와 밤이 겹치면 SOLO_TRIP_OVERLAP으로 거부한다.
     *
     * @param today 한국 날짜 기준 오늘
     */
    public static SoloTrip register(
            long memberId, long spotId, Schedule schedule, LocalDate today, List<SoloTrip> upcoming, Instant now) {
        requireValidSchedule(schedule, today);
        if (upcoming.size() >= MAX_UPCOMING_PER_MEMBER) {
            throw new BusinessException(TripErrorCode.SOLO_TRIP_LIMIT);
        }
        if (upcoming.stream().anyMatch(trip -> trip.overlaps(schedule))) {
            throw new BusinessException(TripErrorCode.SOLO_TRIP_OVERLAP);
        }
        return new SoloTrip(memberId, spotId, schedule.startDate(), schedule.endDate(), now);
    }

    /** memberId인 회원이 등록한 일정이면 true다. */
    public boolean isRegisteredBy(long memberId) {
        return this.memberId == memberId;
    }

    // 종료일 밤은 일정에 들지 않으므로, 한 일정의 종료일에 다른 일정이 출발하면 겹치지 않는다.
    private boolean overlaps(Schedule other) {
        return startDate.isBefore(other.endDate()) && other.startDate().isBefore(endDate);
    }

    private static void requireValidSchedule(Schedule schedule, LocalDate today) {
        if (schedule == null || schedule.startDate() == null || schedule.endDate() == null) {
            throw new BusinessException(TripErrorCode.SOLO_TRIP_SCHEDULE_INVALID);
        }
        long daysUntilStart = ChronoUnit.DAYS.between(today, schedule.startDate());
        long nights = ChronoUnit.DAYS.between(schedule.startDate(), schedule.endDate());
        boolean startInRange = daysUntilStart >= 1 && daysUntilStart <= MAX_DAYS_UNTIL_START;
        boolean nightsInRange = nights >= MIN_NIGHTS && nights <= MAX_NIGHTS;
        if (!startInRange || !nightsInRange) {
            throw new BusinessException(TripErrorCode.SOLO_TRIP_SCHEDULE_INVALID);
        }
    }

    /** 출발일과 종료일(한국 날짜). 검증은 {@link SoloTrip#register}가 한다. */
    public record Schedule(LocalDate startDate, LocalDate endDate) {}
}
