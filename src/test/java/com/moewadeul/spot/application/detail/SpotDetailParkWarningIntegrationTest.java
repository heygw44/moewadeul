package com.moewadeul.spot.application.detail;

import static com.moewadeul.common.testsupport.ProtectedAreaFixture.aProtectedArea;
import static com.moewadeul.common.testsupport.SpotFixture.aSpot;
import static com.moewadeul.member.domain.MemberBuilder.aMember;
import static com.moewadeul.spot.application.detail.SpotDetailFixture.RIDGE_LAT;
import static com.moewadeul.spot.application.detail.SpotDetailFixture.RIDGE_LNG;
import static org.assertj.core.api.Assertions.assertThat;

import com.moewadeul.common.testsupport.IntegrationTest;
import com.moewadeul.common.testsupport.MemberFixture;
import com.moewadeul.common.testsupport.ProtectedAreaFixture;
import com.moewadeul.common.testsupport.SpotFixture;
import com.moewadeul.common.testsupport.TestSequence;
import com.moewadeul.member.domain.MemberStatus;
import com.moewadeul.spot.application.SpotParkWarning;
import com.moewadeul.spot.application.park.ProtectedAreaSource;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

@IntegrationTest
class SpotDetailParkWarningIntegrationTest {

    @Autowired
    private MemberFixture memberFixture;

    @Autowired
    private ProtectedAreaFixture protectedAreaFixture;

    @Autowired
    private SpotFixture spotFixture;

    @Autowired
    private SpotDetailQueryService spotDetailQueryService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private SpotDetailFixture fixture;

    @BeforeEach
    void setUp() {
        fixture = new SpotDetailFixture(jdbcTemplate, memberFixture);
    }

    @Test
    @DisplayName("[F-05] 경계 행이 있는 경고 박지는 공원 이름, 출처, 기준일, 참고용 고지, 안내를 채운다")
    void fillsParkWarningWithAreaForWarnedBakji() {
        // given
        long areaId = protectedAreaFixture.insert(aProtectedArea().name("설악산 국립공원"));
        long spotId = insertWarnedBakji(areaId);

        // when
        SpotDetail detail = spotDetailQueryService.findDetail(spotId);

        // then
        assertThat(detail.parkWarning())
                .isEqualTo(new SpotParkWarning(
                        true,
                        "설악산 국립공원",
                        ProtectedAreaSource.KDPA,
                        LocalDate.parse("2026-01-01"),
                        "공원 경계는 참고 자료예요. 정확한 경계는 공식 고시 도면을 확인해 주세요.",
                        "자연공원 안에서는 지정된 야영장 밖의 야영과 취사가 금지돼 있고 어기면 과태료 대상이에요. 이곳에서는 야영하지 말고 가까운 지정 야영장을 찾아 주세요."));
    }

    @Test
    @DisplayName("[F-05] 경계 행이 없는 경고 박지는 공원 이름, 출처, 기준일만 비우고 고지와 안내는 채운다")
    void fillsNoticeAndGuideForWarnedBakjiWithoutArea() {
        // given
        long spotId = insertWarnedBakji(null);

        // when
        SpotDetail detail = spotDetailQueryService.findDetail(spotId);

        // then
        assertThat(detail.parkWarning())
                .isEqualTo(new SpotParkWarning(
                        true,
                        null,
                        null,
                        null,
                        "공원 경계는 참고 자료예요. 정확한 경계는 공식 고시 도면을 확인해 주세요.",
                        "자연공원 안에서는 지정된 야영장 밖의 야영과 취사가 금지돼 있고 어기면 과태료 대상이에요. 이곳에서는 야영하지 말고 가까운 지정 야영장을 찾아 주세요."));
    }

    @Test
    @DisplayName("[F-05] 경고가 아닌 장소는 warned만 false이고 나머지 경고 필드는 null이다")
    void leavesOtherParkWarningFieldsNullWhenNotWarned() {
        // given
        long spotId = spotFixture.insert(aSpot().type("CAMPSITE").at(RIDGE_LAT, RIDGE_LNG));
        fixture.insertPublicDetail(spotId, "GOCAMPING", null, null, "OPERATING", null, null);

        // when
        SpotDetail detail = spotDetailQueryService.findDetail(spotId);

        // then
        assertThat(detail.parkWarning()).isEqualTo(new SpotParkWarning(false, null, null, null, null, null));
    }

    private long insertWarnedBakji(Long areaId) {
        String name = TestSequence.unique("경고박지");
        long spotId = spotFixture.insert(aSpot().name(name).parkWarning(true).at(RIDGE_LAT, RIDGE_LNG));
        jdbcTemplate.update("UPDATE spot SET protected_area_id = ? WHERE id = ?", areaId, spotId);
        jdbcTemplate.update(
                "INSERT INTO bakji_detail (spot_id, reporter_id, has_water, has_toilet, created_at, updated_at)"
                        + " VALUES (?, ?, FALSE, FALSE, NOW(6), NOW(6))",
                spotId,
                memberFixture.save(aMember().status(MemberStatus.ACTIVE)).getId());
        return spotId;
    }
}
