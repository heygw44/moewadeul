package com.moewadeul.spot.application.publicspot;

import static com.moewadeul.spot.application.publicspot.PublicSpotSyncFixture.NEXT_SYNC;
import static com.moewadeul.spot.application.publicspot.PublicSpotSyncFixture.SEOUL_LATITUDE;
import static com.moewadeul.spot.application.publicspot.PublicSpotSyncFixture.SEOUL_LONGITUDE;
import static com.moewadeul.spot.application.publicspot.PublicSpotSyncFixture.campsite;
import static com.moewadeul.spot.application.publicspot.PublicSpotSyncFixture.forest;
import static com.moewadeul.spot.application.publicspot.PublicSpotSyncFixture.seoulCampsite;
import static org.assertj.core.api.Assertions.assertThat;

import com.moewadeul.common.testsupport.IntegrationTest;
import com.moewadeul.common.testsupport.MutableClock;
import com.moewadeul.spot.application.PublicSpotOperatingStatus;
import com.moewadeul.spot.application.publicspot.PublicSpotSyncFixture.StoredSpot;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

@IntegrationTest
class PublicSpotOperatingStatusUpsertServiceIntegrationTest {

    @Autowired
    private PublicSpotSyncService publicSpotSyncService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private MutableClock clock;

    private PublicSpotSyncFixture fixture;

    @BeforeEach
    void setUp() {
        fixture = new PublicSpotSyncFixture(jdbcTemplate);
    }

    @Test
    @DisplayName("[F-06] 운영 상태와 휴장 기간을 상세에 저장하고, 값이 없으면 NULL로 저장한다")
    void storesOperatingStatusAndClosedDates() {
        // given
        PublicSpotCommand closed = campsite(
                "100",
                "솔숲 야영장",
                SEOUL_LATITUDE,
                SEOUL_LONGITUDE,
                PublicSpotOperatingStatus.TEMPORARILY_CLOSED,
                LocalDate.parse("2026-11-16"),
                LocalDate.parse("2027-03-15"));
        PublicSpotCommand noStatus = seoulCampsite("101", "갈대 야영장");

        // when
        PublicSpotUpsertResult result =
                publicSpotSyncService.upsert(PublicSpotSource.GOCAMPING, List.of(closed, noStatus));

        // then
        assertThat(result).isEqualTo(new PublicSpotUpsertResult(2, 0, 0, 0));
        StoredSpot stored = fixture.findSpot(PublicSpotSource.GOCAMPING, "100");
        assertThat(stored.operatingStatus()).isEqualTo("TEMPORARILY_CLOSED");
        assertThat(stored.closedFrom()).isEqualTo(LocalDate.parse("2026-11-16"));
        assertThat(stored.closedUntil()).isEqualTo(LocalDate.parse("2027-03-15"));
        assertThat(stored.sourceRemovedAt()).isNull();
        StoredSpot empty = fixture.findSpot(PublicSpotSource.GOCAMPING, "101");
        assertThat(empty.operatingStatus()).isNull();
        assertThat(empty.closedFrom()).isNull();
        assertThat(empty.closedUntil()).isNull();
    }

    @Test
    @DisplayName("[F-06] 운영 상태와 휴장 기간이 있는 데이터를 두 번 적재해도 수정 시각과 동기화 시각이 바뀌지 않는다")
    void upsertingSameStatusAndDatesTwiceLeavesRowsUntouched() {
        // given
        List<PublicSpotCommand> batch = List.of(campsite(
                "100",
                "솔숲 야영장",
                SEOUL_LATITUDE,
                SEOUL_LONGITUDE,
                PublicSpotOperatingStatus.TEMPORARILY_CLOSED,
                LocalDate.parse("2026-11-16"),
                LocalDate.parse("2027-03-15")));
        publicSpotSyncService.upsert(PublicSpotSource.GOCAMPING, batch);
        clock.advance(NEXT_SYNC);

        // when
        PublicSpotUpsertResult result = publicSpotSyncService.upsert(PublicSpotSource.GOCAMPING, batch);

        // then
        assertThat(result).isEqualTo(new PublicSpotUpsertResult(0, 0, 1, 0));
        StoredSpot spot = fixture.findSpot(PublicSpotSource.GOCAMPING, "100");
        assertThat(spot.updatedAt()).isEqualTo(MutableClock.DEFAULT_INSTANT);
        assertThat(spot.detailUpdatedAt()).isEqualTo(MutableClock.DEFAULT_INSTANT);
        assertThat(spot.syncedAt()).isEqualTo(MutableClock.DEFAULT_INSTANT);
    }

    @Test
    @DisplayName("[F-06] 운영 상태나 휴장 기간만 바뀌면 상세와 동기화 시각만 갱신하고 장소의 수정 시각은 그대로 둔다")
    void operatingStatusOnlyChangeUpdatesDetailButNotSpot() {
        // given
        publicSpotSyncService.upsert(
                PublicSpotSource.GOCAMPING,
                List.of(campsite(
                        "100",
                        "솔숲 야영장",
                        SEOUL_LATITUDE,
                        SEOUL_LONGITUDE,
                        PublicSpotOperatingStatus.OPERATING,
                        null,
                        null)));
        clock.advance(NEXT_SYNC);
        PublicSpotCommand changed = campsite(
                "100",
                "솔숲 야영장",
                SEOUL_LATITUDE,
                SEOUL_LONGITUDE,
                PublicSpotOperatingStatus.TEMPORARILY_CLOSED,
                LocalDate.parse("2026-12-01"),
                LocalDate.parse("2027-02-28"));

        // when
        PublicSpotUpsertResult result = publicSpotSyncService.upsert(PublicSpotSource.GOCAMPING, List.of(changed));

        // then
        assertThat(result).isEqualTo(new PublicSpotUpsertResult(0, 1, 0, 0));
        Instant syncTime = MutableClock.DEFAULT_INSTANT.plus(NEXT_SYNC);
        StoredSpot spot = fixture.findSpot(PublicSpotSource.GOCAMPING, "100");
        assertThat(spot.operatingStatus()).isEqualTo("TEMPORARILY_CLOSED");
        assertThat(spot.closedFrom()).isEqualTo(LocalDate.parse("2026-12-01"));
        assertThat(spot.closedUntil()).isEqualTo(LocalDate.parse("2027-02-28"));
        assertThat(spot.syncedAt()).isEqualTo(syncTime);
        assertThat(spot.detailUpdatedAt()).isEqualTo(syncTime);
        assertThat(spot.updatedAt()).isEqualTo(MutableClock.DEFAULT_INSTANT);
    }

    @Test
    @DisplayName("[F-06] 자연휴양림 출처로 적재하면 FOREST 장소로 저장하고, 기준일만 바뀌어도 상세를 갱신한다")
    void forestUpsertStoresSourceDateAndUpdatesWhenOnlySourceDateChanges() {
        // given
        LocalDate firstDate = LocalDate.parse("2026-03-25");
        LocalDate nextDate = LocalDate.parse("2026-09-25");
        PublicSpotCommand first = forest("F-1", firstDate);

        // when
        PublicSpotUpsertResult inserted = publicSpotSyncService.upsert(PublicSpotSource.FOREST, List.of(first));
        PublicSpotUpsertResult again = publicSpotSyncService.upsert(PublicSpotSource.FOREST, List.of(first));
        clock.advance(NEXT_SYNC);
        PublicSpotUpsertResult updated =
                publicSpotSyncService.upsert(PublicSpotSource.FOREST, List.of(forest("F-1", nextDate)));

        // then
        assertThat(inserted).isEqualTo(new PublicSpotUpsertResult(1, 0, 0, 0));
        assertThat(again).isEqualTo(new PublicSpotUpsertResult(0, 0, 1, 0));
        assertThat(updated).isEqualTo(new PublicSpotUpsertResult(0, 1, 0, 0));
        StoredSpot spot = fixture.findSpot(PublicSpotSource.FOREST, "F-1");
        assertThat(spot.type()).isEqualTo("FOREST");
        assertThat(spot.detailUpdatedAt()).isEqualTo(MutableClock.DEFAULT_INSTANT.plus(NEXT_SYNC));
        assertThat(fixture.findSourceDate(PublicSpotSource.FOREST, "F-1")).isEqualTo(nextDate);
    }

    @Test
    @DisplayName("[F-06] 자연휴양림 출처로 처음 적재하면 기준일을 public_spot_detail.source_date에 저장한다")
    void forestUpsertStoresSourceDate() {
        // given
        LocalDate sourceDate = LocalDate.parse("2026-03-25");

        // when
        publicSpotSyncService.upsert(PublicSpotSource.FOREST, List.of(forest("F-1", sourceDate)));

        // then
        assertThat(fixture.findSourceDate(PublicSpotSource.FOREST, "F-1")).isEqualTo(sourceDate);
    }
}
