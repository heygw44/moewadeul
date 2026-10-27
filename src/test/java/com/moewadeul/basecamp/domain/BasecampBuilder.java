package com.moewadeul.basecamp.domain;

import com.moewadeul.common.testsupport.MutableClock;
import java.time.Instant;
import java.time.LocalDate;
import org.springframework.test.util.ReflectionTestUtils;

public class BasecampBuilder {

    public static final long LEADER_ID = 1L;
    public static final long MEMBER_ID = 2L;
    public static final long APPLICANT_ID = 3L;
    public static final long OUTSIDER_ID = 9L;
    public static final Instant NOW = MutableClock.DEFAULT_INSTANT;
    public static final Instant COMPLETED_AT = NOW.plusSeconds(3600);
    public static final LocalDate DEFAULT_START_DATE = LocalDate.of(2026, 10, 20);

    private static final long APPLICATION_ID_OFFSET = 1000L;
    private static final long SPOT_ID = 10L;
    private static final int DEFAULT_CAPACITY = 4;
    private static final int DEFAULT_NIGHTS = 2;

    private int capacity = DEFAULT_CAPACITY;
    private LocalDate startDate = DEFAULT_START_DATE;

    public static BasecampBuilder aBasecamp() {
        return new BasecampBuilder();
    }

    public static long applicationIdOf(long applicantId) {
        return APPLICATION_ID_OFFSET + applicantId;
    }

    public static BasecampDetails details(int capacity, LocalDate startDate) {
        return new BasecampDetails(
                "북한산 백패킹",
                "함께 가요",
                startDate,
                startDate.plusDays(DEFAULT_NIGHTS),
                Capacity.of(capacity),
                JoinCondition.none());
    }

    public static void join(Basecamp basecamp, long applicantId) {
        BasecampApplication application = apply(basecamp, applicantId);
        basecamp.approve(application.getId(), NOW);
    }

    public static BasecampApplication apply(Basecamp basecamp, long applicantId) {
        BasecampApplication application = basecamp.apply(applicantId, "같이 가고 싶어요", NOW);
        ReflectionTestUtils.setField(application, "id", applicationIdOf(applicantId));
        return application;
    }

    public BasecampBuilder capacity(int capacity) {
        this.capacity = capacity;
        return this;
    }

    public BasecampBuilder startDate(LocalDate startDate) {
        this.startDate = startDate;
        return this;
    }

    public Basecamp openOnly() {
        return Basecamp.open(LEADER_ID, SPOT_ID, details(capacity, startDate), NOW);
    }

    public Basecamp inState(BasecampStatus state) {
        Basecamp basecamp = openOnly();
        join(basecamp, MEMBER_ID);
        apply(basecamp, APPLICANT_ID);
        move(basecamp, state);
        return basecamp;
    }

    public Basecamp autoClosed() {
        Basecamp basecamp = capacity(3).openOnly();
        join(basecamp, MEMBER_ID);
        join(basecamp, APPLICANT_ID);
        return basecamp;
    }

    private static void move(Basecamp basecamp, BasecampStatus state) {
        switch (state) {
            case RECRUITING -> {}
            case CLOSED -> basecamp.close(NOW);
            case CONFIRMED -> basecamp.confirm(NOW);
            case COMPLETED -> {
                basecamp.confirm(NOW);
                basecamp.complete(COMPLETED_AT);
            }
            case CANCELED -> basecamp.cancel(CancelReason.LEADER, NOW);
        }
    }
}
