package com.moewadeul.spot.application.publicspot;

import static com.moewadeul.spot.application.publicspot.PublicSpotSyncFixture.NEXT_SYNC;
import static com.moewadeul.spot.application.publicspot.PublicSpotSyncFixture.seoulCampsite;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.moewadeul.common.testsupport.IntegrationTest;
import com.moewadeul.common.testsupport.MutableClock;
import com.moewadeul.spot.application.publicspot.PublicSpotSyncFixture.StoredSpot;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

@IntegrationTest
class PublicSpotMarkMissingRemovedServiceIntegrationTest {

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
    @DisplayName("[F-06] 현재 목록에 없는 외부 ID의 장소만 숨기고, 다른 출처의 장소는 건드리지 않는다")
    void markMissingRemovedHidesOnlyMissingIdsOfGivenSource() {
        // given
        publicSpotSyncService.upsert(
                PublicSpotSource.FOREST, List.of(seoulCampsite("100", "솔숲 휴양림"), seoulCampsite("101", "갈대 휴양림")));
        publicSpotSyncService.upsert(PublicSpotSource.GOCAMPING, List.of(seoulCampsite("101", "갈대 야영장")));

        // when
        int hidden = publicSpotSyncService.markMissingRemoved(PublicSpotSource.FOREST, List.of("100"));

        // then
        assertThat(hidden).isEqualTo(1);
        assertThat(fixture.findSpot(PublicSpotSource.FOREST, "100").status()).isEqualTo("ACTIVE");
        StoredSpot missing = fixture.findSpot(PublicSpotSource.FOREST, "101");
        assertThat(missing.status()).isEqualTo("HIDDEN");
        assertThat(missing.sourceRemovedAt()).isNotNull();
        StoredSpot otherSource = fixture.findSpot(PublicSpotSource.GOCAMPING, "101");
        assertThat(otherSource.status()).isEqualTo("ACTIVE");
        assertThat(otherSource.sourceRemovedAt()).isNull();
    }

    @Test
    @DisplayName("[F-06] 모든 외부 ID가 목록에 있으면 아무것도 숨기지 않고 0을 돌려준다")
    void markMissingRemovedReturnsZeroWhenAllPresent() {
        // given
        publicSpotSyncService.upsert(
                PublicSpotSource.FOREST, List.of(seoulCampsite("100", "솔숲 휴양림"), seoulCampsite("101", "갈대 휴양림")));

        // when
        int hidden = publicSpotSyncService.markMissingRemoved(PublicSpotSource.FOREST, List.of("100", "101"));

        // then
        assertThat(hidden).isZero();
        assertThat(fixture.findSpot(PublicSpotSource.FOREST, "100").status()).isEqualTo("ACTIVE");
        assertThat(fixture.findSpot(PublicSpotSource.FOREST, "101").status()).isEqualTo("ACTIVE");
    }

    @Test
    @DisplayName("[F-06] 원천 목록이 비어 있으면 예외를 던지고 아무 장소도 숨기지 않는다")
    void markMissingRemovedRejectsEmptyPresentList() {
        // given
        publicSpotSyncService.upsert(PublicSpotSource.FOREST, List.of(seoulCampsite("100", "솔숲 휴양림")));

        // when & then
        assertThatThrownBy(() -> publicSpotSyncService.markMissingRemoved(PublicSpotSource.FOREST, List.of(" ")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(fixture.findSpot(PublicSpotSource.FOREST, "100").status()).isEqualTo("ACTIVE");
    }

    @Test
    @DisplayName("[F-06] 이미 숨긴 표시가 있거나 관리자가 숨긴 장소는 목록에 없어도 다시 숨기지 않는다")
    void markMissingRemovedSkipsAlreadyMarkedAndAdminHiddenSpots() {
        // given
        publicSpotSyncService.upsert(
                PublicSpotSource.FOREST,
                List.of(
                        seoulCampsite("100", "솔숲 휴양림"),
                        seoulCampsite("101", "갈대 휴양림"),
                        seoulCampsite("102", "달빛 휴양림")));
        publicSpotSyncService.markRemoved(PublicSpotSource.FOREST, List.of("100"));
        Instant firstMarker = fixture.findSpot(PublicSpotSource.FOREST, "100").sourceRemovedAt();
        fixture.setSpotStatus(fixture.findSpotId(PublicSpotSource.FOREST, "101"), "HIDDEN");
        clock.advance(NEXT_SYNC);

        // when
        int hidden = publicSpotSyncService.markMissingRemoved(PublicSpotSource.FOREST, List.of("102"));

        // then
        assertThat(hidden).isZero();
        assertThat(fixture.findSpot(PublicSpotSource.FOREST, "100").sourceRemovedAt())
                .isEqualTo(firstMarker);
        StoredSpot adminHidden = fixture.findSpot(PublicSpotSource.FOREST, "101");
        assertThat(adminHidden.status()).isEqualTo("HIDDEN");
        assertThat(adminHidden.sourceRemovedAt()).isNull();
    }

    @Test
    @DisplayName("[F-06] 목록의 외부 ID에 앞뒤 공백이 있어도 그 장소는 목록에 있는 것으로 본다")
    void markMissingRemovedTreatsPaddedPresentIdsAsPresent() {
        // given
        publicSpotSyncService.upsert(
                PublicSpotSource.FOREST, List.of(seoulCampsite("100", "솔숲 휴양림"), seoulCampsite("101", "갈대 휴양림")));

        // when
        int hidden = publicSpotSyncService.markMissingRemoved(PublicSpotSource.FOREST, List.of(" 100 ", "101\t"));

        // then
        assertThat(hidden).isZero();
        assertThat(fixture.findSpot(PublicSpotSource.FOREST, "100").status()).isEqualTo("ACTIVE");
    }
}
