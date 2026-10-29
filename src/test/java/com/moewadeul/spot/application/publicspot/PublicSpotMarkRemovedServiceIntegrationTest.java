package com.moewadeul.spot.application.publicspot;

import static com.moewadeul.spot.application.publicspot.PublicSpotSyncFixture.NEXT_SYNC;
import static com.moewadeul.spot.application.publicspot.PublicSpotSyncFixture.forest;
import static com.moewadeul.spot.application.publicspot.PublicSpotSyncFixture.seoulCampsite;
import static org.assertj.core.api.Assertions.assertThat;

import com.moewadeul.common.testsupport.IntegrationTest;
import com.moewadeul.common.testsupport.MutableClock;
import com.moewadeul.spot.application.publicspot.PublicSpotSyncFixture.StoredSpot;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

@IntegrationTest
class PublicSpotMarkRemovedServiceIntegrationTest {

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
    @DisplayName("[F-06] 원천이 삭제로 알린 장소를 HIDDEN으로 바꾸고 숨긴 시각을 남긴 뒤 숨긴 수를 돌려준다")
    void markRemovedHidesActiveSpot() {
        // given
        publicSpotSyncService.upsert(PublicSpotSource.GOCAMPING, List.of(seoulCampsite("100", "솔숲 야영장")));
        clock.advance(NEXT_SYNC);

        // when
        int hidden = publicSpotSyncService.markRemoved(PublicSpotSource.GOCAMPING, List.of("100"));

        // then
        assertThat(hidden).isEqualTo(1);
        Instant removeTime = MutableClock.DEFAULT_INSTANT.plus(NEXT_SYNC);
        StoredSpot spot = fixture.findSpot(PublicSpotSource.GOCAMPING, "100");
        assertThat(spot.status()).isEqualTo("HIDDEN");
        assertThat(spot.updatedAt()).isEqualTo(removeTime);
        assertThat(spot.sourceRemovedAt()).isEqualTo(removeTime);
        assertThat(spot.detailUpdatedAt()).isEqualTo(removeTime);
        assertThat(spot.syncedAt()).isEqualTo(MutableClock.DEFAULT_INSTANT);
    }

    @Test
    @DisplayName("[F-06] 같은 삭제 항목을 다시 처리하면 0을 돌려주고 이미 숨긴 장소의 시각을 바꾸지 않는다")
    void markRemovedTwiceIsIdempotent() {
        // given
        publicSpotSyncService.upsert(PublicSpotSource.GOCAMPING, List.of(seoulCampsite("100", "솔숲 야영장")));
        clock.advance(NEXT_SYNC);
        publicSpotSyncService.markRemoved(PublicSpotSource.GOCAMPING, List.of("100"));
        clock.advance(NEXT_SYNC);

        // when
        int hidden = publicSpotSyncService.markRemoved(PublicSpotSource.GOCAMPING, List.of("100"));

        // then
        assertThat(hidden).isZero();
        Instant firstRemoveTime = MutableClock.DEFAULT_INSTANT.plus(NEXT_SYNC);
        StoredSpot spot = fixture.findSpot(PublicSpotSource.GOCAMPING, "100");
        assertThat(spot.status()).isEqualTo("HIDDEN");
        assertThat(spot.updatedAt()).isEqualTo(firstRemoveTime);
        assertThat(spot.sourceRemovedAt()).isEqualTo(firstRemoveTime);
        assertThat(spot.detailUpdatedAt()).isEqualTo(firstRemoveTime);
    }

    @Test
    @DisplayName("[F-06] 관리자가 이미 숨긴 장소는 삭제 항목이 와도 건드리지도 표시하지도 않는다")
    void markRemovedLeavesAdminHiddenSpotUntouched() {
        // given
        publicSpotSyncService.upsert(PublicSpotSource.GOCAMPING, List.of(seoulCampsite("100", "솔숲 야영장")));
        fixture.setSpotStatus(fixture.findSpotId(PublicSpotSource.GOCAMPING, "100"), "HIDDEN");
        clock.advance(NEXT_SYNC);

        // when
        int hidden = publicSpotSyncService.markRemoved(PublicSpotSource.GOCAMPING, List.of("100"));

        // then
        assertThat(hidden).isZero();
        StoredSpot spot = fixture.findSpot(PublicSpotSource.GOCAMPING, "100");
        assertThat(spot.status()).isEqualTo("HIDDEN");
        assertThat(spot.sourceRemovedAt()).isNull();
        assertThat(spot.updatedAt()).isEqualTo(MutableClock.DEFAULT_INSTANT);
        assertThat(spot.detailUpdatedAt()).isEqualTo(MutableClock.DEFAULT_INSTANT);
    }

    @Test
    @DisplayName("[F-06] 관리자가 다시 공개한 장소(ACTIVE이고 삭제 표시가 남은 장소)는 삭제 항목이 또 와도 다시 숨기지 않는다")
    void markRemovedDoesNotHideAdminRestoredSpotAgain() {
        // given
        publicSpotSyncService.upsert(PublicSpotSource.GOCAMPING, List.of(seoulCampsite("100", "솔숲 야영장")));
        long spotId = fixture.findSpotId(PublicSpotSource.GOCAMPING, "100");
        Instant earlier = MutableClock.DEFAULT_INSTANT.minus(NEXT_SYNC);
        fixture.markSourceRemovedBySql(spotId, earlier);
        clock.advance(NEXT_SYNC);

        // when
        int hidden = publicSpotSyncService.markRemoved(PublicSpotSource.GOCAMPING, List.of("100"));

        // then
        assertThat(hidden).isZero();
        StoredSpot spot = fixture.findSpot(PublicSpotSource.GOCAMPING, "100");
        assertThat(spot.status()).isEqualTo("ACTIVE");
        assertThat(spot.sourceRemovedAt()).isEqualTo(earlier);
        assertThat(spot.updatedAt()).isEqualTo(MutableClock.DEFAULT_INSTANT);
    }

    @Test
    @DisplayName("[F-06] DB에 없는 외부 ID, 빈 외부 ID, 빈 목록은 숨긴 장소 없이 0을 돌려준다")
    void markRemovedIgnoresUnknownBlankAndEmptyIds() {
        // given
        publicSpotSyncService.upsert(PublicSpotSource.GOCAMPING, List.of(seoulCampsite("100", "솔숲 야영장")));
        String tooLong = "9".repeat(NormalizedPublicSpot.MAX_EXTERNAL_ID_LENGTH + 1);

        // when
        int unknown = publicSpotSyncService.markRemoved(PublicSpotSource.GOCAMPING, List.of("999", " ", "", tooLong));
        int empty = publicSpotSyncService.markRemoved(PublicSpotSource.GOCAMPING, List.of());

        // then
        assertThat(unknown).isZero();
        assertThat(empty).isZero();
        StoredSpot spot = fixture.findSpot(PublicSpotSource.GOCAMPING, "100");
        assertThat(spot.status()).isEqualTo("ACTIVE");
        assertThat(spot.sourceRemovedAt()).isNull();
    }

    @Test
    @DisplayName("[F-06] 지정한 출처의 장소만 숨기고, 외부 ID가 같은 다른 출처의 장소는 건드리지 않는다")
    void markRemovedHidesOnlyGivenSource() {
        // given
        publicSpotSyncService.upsert(PublicSpotSource.GOCAMPING, List.of(seoulCampsite("100", "솔숲 야영장")));
        publicSpotSyncService.upsert(PublicSpotSource.FOREST, List.of(seoulCampsite("100", "솔숲 휴양림")));
        publicSpotSyncService.upsert(PublicSpotSource.GOCAMPING, List.of(seoulCampsite("101", "갈대 야영장")));

        // when
        int hidden = publicSpotSyncService.markRemoved(PublicSpotSource.GOCAMPING, List.of("100", "999"));

        // then
        assertThat(hidden).isEqualTo(1);
        assertThat(fixture.findSpot(PublicSpotSource.GOCAMPING, "100").status()).isEqualTo("HIDDEN");
        StoredSpot forest = fixture.findSpot(PublicSpotSource.FOREST, "100");
        assertThat(forest.status()).isEqualTo("ACTIVE");
        assertThat(forest.sourceRemovedAt()).isNull();
        StoredSpot other = fixture.findSpot(PublicSpotSource.GOCAMPING, "101");
        assertThat(other.status()).isEqualTo("ACTIVE");
        assertThat(other.sourceRemovedAt()).isNull();
    }

    @Test
    @DisplayName("[F-06] 여러 장소를 한 번에 숨기면 숨긴 수만 세고, 이미 숨긴 장소는 세지 않는다")
    void markRemovedCountsOnlySpotsActuallyHidden() {
        // given
        publicSpotSyncService.upsert(
                PublicSpotSource.GOCAMPING,
                List.of(
                        seoulCampsite("100", "솔숲 야영장"),
                        seoulCampsite("101", "갈대 야영장"),
                        seoulCampsite("102", "달빛 야영장")));
        fixture.setSpotStatus(fixture.findSpotId(PublicSpotSource.GOCAMPING, "102"), "HIDDEN");

        // when
        int hidden = publicSpotSyncService.markRemoved(PublicSpotSource.GOCAMPING, Set.of("100", "101", "102"));

        // then
        assertThat(hidden).isEqualTo(2);
        assertThat(fixture.findSpot(PublicSpotSource.GOCAMPING, "102").sourceRemovedAt())
                .isNull();
    }
}
