package com.moewadeul.trip.domain;

import com.moewadeul.common.error.BusinessException;
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

@Entity
@Table(name = "solo_trip")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SoloTrip {

    public static final int MAX_DAYS_UNTIL_START = 60;

    public static final int MIN_NIGHTS = 1;
    public static final int MAX_NIGHTS = 3;

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

    public boolean isRegisteredBy(long memberId) {
        return this.memberId == memberId;
    }

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

    public record Schedule(LocalDate startDate, LocalDate endDate) {}
}
