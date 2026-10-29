package com.moewadeul.spot.application.detail;

import static com.moewadeul.common.testsupport.SpotFixture.aSpot;
import static com.moewadeul.spot.application.detail.SpotDetailFixture.RIDGE_LAT;
import static com.moewadeul.spot.application.detail.SpotDetailFixture.RIDGE_LNG;
import static org.assertj.core.api.Assertions.assertThat;

import com.moewadeul.common.testsupport.IntegrationTest;
import com.moewadeul.common.testsupport.MemberFixture;
import com.moewadeul.common.testsupport.MutableClock;
import com.moewadeul.common.testsupport.SpotFixture;
import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

@IntegrationTest
class SpotDetailRecruitingBasecampsIntegrationTest {

    @Autowired
    private MemberFixture memberFixture;

    @Autowired
    private SpotFixture spotFixture;

    @Autowired
    private SpotDetailQueryService spotDetailQueryService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private MutableClock clock;

    private SpotDetailFixture fixture;

    @BeforeEach
    void setUp() {
        fixture = new SpotDetailFixture(jdbcTemplate, memberFixture);
    }

    @Test
    @DisplayName("[F-05] 모집 중인 베이스캠프만 출발일, ID 순서로 인원과 합류 조건을 채워 준다")
    void listsOnlyRecruitingBasecampsInOrder() {
        // given
        clock.setInstant(Instant.parse("2026-11-01T03:00:00Z"));
        long spotId = spotFixture.insert(aSpot().at(RIDGE_LAT, RIDGE_LNG));
        long otherSpotId = spotFixture.insert(aSpot().at(RIDGE_LAT, RIDGE_LNG));
        long later = fixture.insertBasecamp(spotId, "RECRUITING", "2026-11-14", "2026-11-16", 4, "늦은 출발");
        long sameDayFirst = fixture.insertBasecamp(spotId, "RECRUITING", "2026-11-07", "2026-11-08", 6, "같은 날 먼저");
        long sameDaySecond = fixture.insertBasecamp(spotId, "RECRUITING", "2026-11-07", "2026-11-09", 3, "같은 날 나중");
        fixture.insertBasecamp(spotId, "CLOSED", "2026-11-05", "2026-11-06");
        fixture.insertBasecamp(spotId, "CONFIRMED", "2026-11-05", "2026-11-06");
        fixture.insertBasecamp(otherSpotId, "RECRUITING", "2026-11-05", "2026-11-06");
        fixture.insertBasecampMembers(sameDayFirst, "ACTIVE", 3);
        fixture.insertBasecampMembers(sameDayFirst, "LEFT", 1);
        fixture.insertBasecampMembers(later, "ACTIVE", 1);
        jdbcTemplate.update(
                "UPDATE basecamp SET min_trust_level = 1, age_group_min = 20, age_group_max = 30,"
                        + " same_gender_only = TRUE, required_gender = 'FEMALE' WHERE id = ?",
                sameDayFirst);

        // when
        SpotDetail detail = spotDetailQueryService.findDetail(spotId);

        // then
        assertThat(detail.recruitingBasecamps())
                .containsExactly(
                        new SpotRecruitingBasecamp(
                                sameDayFirst,
                                "같은 날 먼저",
                                LocalDate.parse("2026-11-07"),
                                LocalDate.parse("2026-11-08"),
                                6,
                                3,
                                new SpotJoinCondition(1, 20, 30, true)),
                        new SpotRecruitingBasecamp(
                                sameDaySecond,
                                "같은 날 나중",
                                LocalDate.parse("2026-11-07"),
                                LocalDate.parse("2026-11-09"),
                                3,
                                0,
                                new SpotJoinCondition(null, null, null, false)),
                        new SpotRecruitingBasecamp(
                                later,
                                "늦은 출발",
                                LocalDate.parse("2026-11-14"),
                                LocalDate.parse("2026-11-16"),
                                4,
                                1,
                                new SpotJoinCondition(null, null, null, false)));
    }

    @Test
    @DisplayName("[F-05] 모집 중인 베이스캠프와 확정 베이스캠프가 없으면 두 목록은 빈 목록이다")
    void returnsEmptyListsWithoutBasecamps() {
        // given
        long spotId = spotFixture.insert(aSpot().at(RIDGE_LAT, RIDGE_LNG));

        // when
        SpotDetail detail = spotDetailQueryService.findDetail(spotId);

        // then
        assertThat(detail.expectedPeople()).isEmpty();
        assertThat(detail.recruitingBasecamps()).isEmpty();
    }
}
