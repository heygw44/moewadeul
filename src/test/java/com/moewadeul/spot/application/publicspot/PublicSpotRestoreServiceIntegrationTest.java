package com.moewadeul.spot.application.publicspot;

import static com.moewadeul.spot.application.publicspot.PublicSpotSyncFixture.NEXT_SYNC;
import static com.moewadeul.spot.application.publicspot.PublicSpotSyncFixture.SEOUL_LATITUDE;
import static com.moewadeul.spot.application.publicspot.PublicSpotSyncFixture.SEOUL_LONGITUDE;
import static com.moewadeul.spot.application.publicspot.PublicSpotSyncFixture.campsite;
import static com.moewadeul.spot.application.publicspot.PublicSpotSyncFixture.seoulCampsite;
import static org.assertj.core.api.Assertions.assertThat;

import com.moewadeul.common.testsupport.IntegrationTest;
import com.moewadeul.common.testsupport.MutableClock;
import com.moewadeul.spot.application.PublicSpotOperatingStatus;
import com.moewadeul.spot.application.publicspot.PublicSpotSyncFixture.StoredSpot;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

@IntegrationTest
class PublicSpotRestoreServiceIntegrationTest {

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
    @DisplayName("[F-06] 원천 삭제로 숨긴 장소가 다시 오면 ACTIVE로 되돌리고 삭제 표시를 지우며 갱신 한 건으로 센다")
    void upsertRestoresSpotHiddenBySourceRemoval() {
        // given
        PublicSpotCommand command = seoulCampsite("100", "솔숲 야영장");
        publicSpotSyncService.upsert(PublicSpotSource.GOCAMPING, List.of(command));
        clock.advance(NEXT_SYNC);
        publicSpotSyncService.markRemoved(PublicSpotSource.GOCAMPING, List.of("100"));
        clock.advance(NEXT_SYNC);

        // when
        PublicSpotUpsertResult result = publicSpotSyncService.upsert(PublicSpotSource.GOCAMPING, List.of(command));

        // then
        assertThat(result).isEqualTo(new PublicSpotUpsertResult(0, 1, 0, 0));
        Instant restoreTime = MutableClock.DEFAULT_INSTANT.plus(NEXT_SYNC.multipliedBy(2));
        StoredSpot spot = fixture.findSpot(PublicSpotSource.GOCAMPING, "100");
        assertThat(spot.status()).isEqualTo("ACTIVE");
        assertThat(spot.sourceRemovedAt()).isNull();
        assertThat(spot.updatedAt()).isEqualTo(restoreTime);
        assertThat(spot.detailUpdatedAt()).isEqualTo(restoreTime);
        assertThat(spot.syncedAt()).isEqualTo(restoreTime);
    }

    @Test
    @DisplayName("[F-06] 되돌리는 장소의 값도 바뀌었으면 그 장소를 갱신 한 건으로만 세고 새 값도 저장한다")
    void restoredSpotWithChangedFieldsIsCountedOnce() {
        // given
        publicSpotSyncService.upsert(PublicSpotSource.GOCAMPING, List.of(seoulCampsite("100", "솔숲 야영장")));
        publicSpotSyncService.markRemoved(PublicSpotSource.GOCAMPING, List.of("100"));
        clock.advance(NEXT_SYNC);
        PublicSpotCommand renamed = campsite(
                "100",
                "솔숲 야영장(새 이름)",
                SEOUL_LATITUDE,
                SEOUL_LONGITUDE,
                PublicSpotOperatingStatus.OPERATING,
                null,
                null);

        // when
        PublicSpotUpsertResult result = publicSpotSyncService.upsert(PublicSpotSource.GOCAMPING, List.of(renamed));

        // then
        assertThat(result).isEqualTo(new PublicSpotUpsertResult(0, 1, 0, 0));
        StoredSpot spot = fixture.findSpot(PublicSpotSource.GOCAMPING, "100");
        assertThat(spot.status()).isEqualTo("ACTIVE");
        assertThat(spot.sourceRemovedAt()).isNull();
        assertThat(spot.name()).isEqualTo("솔숲 야영장(새 이름)");
        assertThat(spot.operatingStatus()).isEqualTo("OPERATING");
    }

    @Test
    @DisplayName("[F-06] 삭제 표시가 있지만 관리자가 이미 ACTIVE로 되돌린 장소는 삭제 표시만 지운다")
    void upsertOnlyClearsMarkerWhenSpotIsAlreadyActive() {
        // given
        PublicSpotCommand command = seoulCampsite("100", "솔숲 야영장");
        publicSpotSyncService.upsert(PublicSpotSource.GOCAMPING, List.of(command));
        fixture.markSourceRemovedBySql(
                fixture.findSpotId(PublicSpotSource.GOCAMPING, "100"), MutableClock.DEFAULT_INSTANT);
        clock.advance(NEXT_SYNC);

        // when
        PublicSpotUpsertResult result = publicSpotSyncService.upsert(PublicSpotSource.GOCAMPING, List.of(command));

        // then
        assertThat(result).isEqualTo(new PublicSpotUpsertResult(0, 1, 0, 0));
        Instant syncTime = MutableClock.DEFAULT_INSTANT.plus(NEXT_SYNC);
        StoredSpot spot = fixture.findSpot(PublicSpotSource.GOCAMPING, "100");
        assertThat(spot.status()).isEqualTo("ACTIVE");
        assertThat(spot.sourceRemovedAt()).isNull();
        assertThat(spot.updatedAt()).isEqualTo(MutableClock.DEFAULT_INSTANT);
        assertThat(spot.detailUpdatedAt()).isEqualTo(syncTime);
    }

    @Test
    @DisplayName("[F-06] 관리자가 숨긴 장소(삭제 표시 없음)는 같은 데이터로 다시 적재해도 HIDDEN으로 남고 바뀌지 않은 것으로 센다")
    void upsertKeepsAdminHiddenSpotHidden() {
        // given
        PublicSpotCommand command = seoulCampsite("100", "솔숲 야영장");
        publicSpotSyncService.upsert(PublicSpotSource.GOCAMPING, List.of(command));
        fixture.setSpotStatus(fixture.findSpotId(PublicSpotSource.GOCAMPING, "100"), "HIDDEN");
        clock.advance(NEXT_SYNC);

        // when
        PublicSpotUpsertResult result = publicSpotSyncService.upsert(PublicSpotSource.GOCAMPING, List.of(command));

        // then
        assertThat(result).isEqualTo(new PublicSpotUpsertResult(0, 0, 1, 0));
        StoredSpot spot = fixture.findSpot(PublicSpotSource.GOCAMPING, "100");
        assertThat(spot.status()).isEqualTo("HIDDEN");
        assertThat(spot.sourceRemovedAt()).isNull();
        assertThat(spot.updatedAt()).isEqualTo(MutableClock.DEFAULT_INSTANT);
        assertThat(spot.syncedAt()).isEqualTo(MutableClock.DEFAULT_INSTANT);
    }
}
