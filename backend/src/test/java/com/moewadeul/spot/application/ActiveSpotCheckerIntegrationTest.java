package com.moewadeul.spot.application;

import static com.moewadeul.common.testsupport.ProtectedAreaFixture.aProtectedArea;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.moewadeul.common.error.BusinessException;
import com.moewadeul.common.error.CommonErrorCode;
import com.moewadeul.common.testsupport.IntegrationTest;
import com.moewadeul.common.testsupport.MutableClock;
import com.moewadeul.common.testsupport.ProtectedAreaFixture;
import com.moewadeul.spot.domain.GeoPoint;
import com.moewadeul.spot.domain.ParkAreaJudgement;
import com.moewadeul.spot.domain.Spot;
import com.moewadeul.spot.infra.SpotJpaRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

@IntegrationTest
class ActiveSpotCheckerIntegrationTest {

    @Autowired
    private ProtectedAreaFixture protectedAreaFixture;

    @Autowired
    private ActiveSpotChecker activeSpotChecker;

    @Autowired
    private SpotJpaRepository spotRepository;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    @DisplayName("[F-12][BC-04] 공원 경계 경고가 붙은 박지이면 true, 경고가 없는 박지이면 false다")
    void warningBakjiIsTrueAndCleanBakjiIsFalse() {
        // given
        long areaId = protectedAreaFixture.insert(aProtectedArea().name("북한산"));
        long warning = saveBakji(ParkAreaJudgement.inside(areaId, MutableClock.DEFAULT_INSTANT));
        long clean = saveBakji(ParkAreaJudgement.outside(MutableClock.DEFAULT_INSTANT));

        // when & then
        assertThat(activeSpotChecker.isWarningBakji(warning)).isTrue();
        assertThat(activeSpotChecker.isWarningBakji(clean)).isFalse();
    }

    @Test
    @DisplayName("[F-12][BC-04] 야영장은 경고 값이 켜져 있어도 false다")
    void campsiteIsNeverWarningBakji() {
        // given
        long areaId = protectedAreaFixture.insert(aProtectedArea().name("북한산"));
        long campsite = saveBakji(ParkAreaJudgement.inside(areaId, MutableClock.DEFAULT_INSTANT));
        jdbc.update("UPDATE spot SET type = 'CAMPSITE' WHERE id = ?", campsite);

        // when & then
        assertThat(activeSpotChecker.isWarningBakji(campsite)).isFalse();
    }

    @Test
    @DisplayName("[F-12] 없는 장소이거나 ACTIVE가 아닌 장소이면 NOT_FOUND로 거부한다")
    void missingOrHiddenSpotIsNotFound() {
        // given
        long areaId = protectedAreaFixture.insert(aProtectedArea().name("북한산"));
        long hidden = saveBakji(ParkAreaJudgement.inside(areaId, MutableClock.DEFAULT_INSTANT));
        jdbc.update("UPDATE spot SET status = 'HIDDEN' WHERE id = ?", hidden);

        // when & then
        assertThatThrownBy(() -> activeSpotChecker.isWarningBakji(hidden))
                .isInstanceOf(BusinessException.class)
                .hasMessage(CommonErrorCode.NOT_FOUND.message());
        assertThatThrownBy(() -> activeSpotChecker.isWarningBakji(hidden + 1000))
                .isInstanceOf(BusinessException.class)
                .hasMessage(CommonErrorCode.NOT_FOUND.message());
    }

    private long saveBakji(ParkAreaJudgement judgement) {
        Spot spot = Spot.bakji("능선 끝 평지", new GeoPoint(37.25, 127.25), judgement, MutableClock.DEFAULT_INSTANT);
        return spotRepository.save(spot).getId();
    }
}
