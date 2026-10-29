package com.moewadeul.trip.domain;

import com.moewadeul.common.error.BusinessException;
import com.moewadeul.common.error.CommonErrorCode;
import com.moewadeul.common.schedule.CampingSchedule;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "solo_trip")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SoloTrip {

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

    public static SoloTrip register(
            long memberId,
            long spotId,
            CampingSchedule schedule,
            LocalDate today,
            List<SoloTrip> upcoming,
            Instant now) {
        if (schedule == null || !schedule.isBookableOn(today)) {
            throw new BusinessException(TripErrorCode.SOLO_TRIP_SCHEDULE_INVALID);
        }
        if (upcoming.size() >= MAX_UPCOMING_PER_MEMBER) {
            throw new BusinessException(TripErrorCode.SOLO_TRIP_LIMIT);
        }
        if (upcoming.stream().anyMatch(trip -> trip.schedule().sharesNightWith(schedule))) {
            throw new BusinessException(TripErrorCode.SOLO_TRIP_OVERLAP);
        }
        return new SoloTrip(memberId, spotId, schedule.startDate(), schedule.endDate(), now);
    }

    public void requireRegisteredBy(long memberId) {
        if (this.memberId != memberId) {
            throw new BusinessException(CommonErrorCode.ACCESS_DENIED);
        }
    }

    private CampingSchedule schedule() {
        return new CampingSchedule(startDate, endDate);
    }
}
