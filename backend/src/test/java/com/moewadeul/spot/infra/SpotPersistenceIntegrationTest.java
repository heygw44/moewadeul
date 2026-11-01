package com.moewadeul.spot.infra;

import static com.moewadeul.common.testsupport.ProtectedAreaFixture.aProtectedArea;
import static org.assertj.core.api.Assertions.assertThat;

import com.moewadeul.common.testsupport.IntegrationTest;
import com.moewadeul.common.testsupport.MutableClock;
import com.moewadeul.common.testsupport.ProtectedAreaFixture;
import com.moewadeul.spot.domain.GeoPoint;
import com.moewadeul.spot.domain.ParkAreaJudgement;
import com.moewadeul.spot.domain.Spot;
import com.moewadeul.spot.domain.SpotRepository;
import com.moewadeul.spot.domain.WeatherGrid;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

@IntegrationTest
class SpotPersistenceIntegrationTest {

    private static final GeoPoint BUKHANSAN = new GeoPoint(37.66, 126.98);
    private static final String BUKHANSAN_BOUNDARY_WKT =
            "MULTIPOLYGON(((126.9 37.6, 127.1 37.6, 127.1 37.7, 126.9 37.7, 126.9 37.6)))";

    @Autowired
    private ProtectedAreaFixture protectedAreaFixture;

    @Autowired
    private SpotRepository spotRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("[F-07] 공원 경계 안으로 판정한 박지를 저장하면 경고, 경계 ID, 판정 시각이 spot 행에 남는다")
    void savesBakjiJudgedInsideParkBoundary() {
        // given
        long protectedAreaId =
                protectedAreaFixture.insert(aProtectedArea().name("북한산국립공원").longLatBoundary(BUKHANSAN_BOUNDARY_WKT));
        Instant now = MutableClock.DEFAULT_INSTANT;
        Instant checkedAt = now.minusSeconds(1);
        Spot bakji = Spot.bakji("능선 아래 평지", BUKHANSAN, ParkAreaJudgement.inside(protectedAreaId, checkedAt), now);

        // when
        long id = spotRepository.save(bakji).getId();
        StoredSpot stored = findStoredSpot(id);

        // then
        assertThat(stored.type()).isEqualTo("BAKJI");
        assertThat(stored.status()).isEqualTo("ACTIVE");
        WeatherGrid expectedGrid = WeatherGrid.from(BUKHANSAN);
        assertThat(stored.weatherNx()).isEqualTo(expectedGrid.nx());
        assertThat(stored.weatherNy()).isEqualTo(expectedGrid.ny());
        assertThat(stored.parkWarning()).isTrue();
        assertThat(stored.protectedAreaId()).isEqualTo(protectedAreaId);
        assertThat(stored.areaCheckedAt()).isEqualTo(checkedAt);
    }

    @Test
    @DisplayName("[F-07] 공원 경계 밖으로 판정한 박지를 저장하면 경고와 경계 ID는 비우고 판정 시각만 남긴다")
    void savesBakjiJudgedOutsideParkBoundary() {
        // given
        Instant now = MutableClock.DEFAULT_INSTANT;
        Spot bakji = Spot.bakji("강변 자갈밭", BUKHANSAN, ParkAreaJudgement.outside(now), now);

        // when
        long id = spotRepository.save(bakji).getId();
        StoredSpot stored = findStoredSpot(id);

        // then
        assertThat(stored.parkWarning()).isFalse();
        assertThat(stored.protectedAreaId()).isNull();
        assertThat(stored.areaCheckedAt()).isEqualTo(now);
    }

    private StoredSpot findStoredSpot(long id) {
        return jdbcTemplate.queryForObject(
                "SELECT type, status, weather_nx, weather_ny, park_warning, protected_area_id, area_checked_at"
                        + " FROM spot WHERE id = ?",
                (rs, rowNum) -> new StoredSpot(
                        rs.getString("type"),
                        rs.getString("status"),
                        rs.getInt("weather_nx"),
                        rs.getInt("weather_ny"),
                        rs.getBoolean("park_warning"),
                        rs.getObject("protected_area_id", Long.class),
                        rs.getObject("area_checked_at", LocalDateTime.class).toInstant(ZoneOffset.UTC)),
                id);
    }

    private record StoredSpot(
            String type,
            String status,
            int weatherNx,
            int weatherNy,
            boolean parkWarning,
            Long protectedAreaId,
            Instant areaCheckedAt) {}
}
