package com.moewadeul.basecamp.infra.query;

import static com.moewadeul.common.testsupport.SpotFixture.aSpot;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.moewadeul.basecamp.application.query.BasecampSpotType;
import com.moewadeul.basecamp.domain.BasecampApplicationStatus;
import com.moewadeul.basecamp.domain.BasecampMemberStatus;
import com.moewadeul.basecamp.domain.BasecampSchedule;
import com.moewadeul.basecamp.domain.BasecampStatus;
import com.moewadeul.basecamp.domain.JoinEligibilityPolicy;
import com.moewadeul.common.profile.Gender;
import com.moewadeul.common.testsupport.IntegrationTest;
import com.moewadeul.common.testsupport.MemberFixture;
import com.moewadeul.common.testsupport.SpotFixture;
import com.moewadeul.member.domain.Member;
import com.moewadeul.spot.application.SpotSearchBox;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

@IntegrationTest
class BasecampSearchMapperIntegrationTest {

    private static final LocalDate DAY = LocalDate.of(2026, 10, 20);
    private static final int BASE_TRUST_LEVEL = JoinEligibilityPolicy.BASE_TRUST_LEVEL.level();

    private static final BasecampSearchCondition.Area AREA = new BasecampSearchCondition.Area(37.0, 127.0, 38.0, 128.0);

    private static final Coordinate CENTER = new Coordinate(37.5665, 126.978);
    private static final Coordinate NORTH_9_8_KM = new Coordinate(37.6546, 126.978);
    private static final Coordinate EAST_10_2_KM = new Coordinate(37.5665, 127.0936);

    @Autowired
    private SpotFixture spotFixture;

    @Autowired
    private MemberFixture memberFixture;

    @Autowired
    private BasecampSearchMapper mapper;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    @DisplayName("[F-12] 영역 조회는 경계선 위의 장소를 포함하고 영역 밖의 장소는 뺀다")
    void areaIncludesBoundaryAndExcludesOutside() {
        // given
        Member leader = memberFixture.save();
        long southWest = insertBasecamp(
                leader, spotFixture.insert(aSpot().name("남서 꼭짓점").at(37.0, 127.0)), "RECRUITING", DAY, 4);
        long northEast = insertBasecamp(
                leader, spotFixture.insert(aSpot().name("북동 꼭짓점").at(38.0, 128.0)), "RECRUITING", DAY, 4);
        long inside =
                insertBasecamp(leader, spotFixture.insert(aSpot().name("안쪽").at(37.5, 127.5)), "RECRUITING", DAY, 4);
        insertBasecamp(leader, spotFixture.insert(aSpot().name("남쪽 바깥").at(36.99, 127.5)), "RECRUITING", DAY, 4);
        insertBasecamp(leader, spotFixture.insert(aSpot().name("동쪽 바깥").at(37.5, 128.01)), "RECRUITING", DAY, 4);

        // when
        List<BasecampSearchRow> rows = mapper.selectRecruiting(areaCondition(AREA), 0, 20);

        // then
        assertThat(rows)
                .extracting(BasecampSearchRow::basecampId)
                .containsExactlyInAnyOrder(southWest, northEast, inside);
    }

    @Test
    @DisplayName("[F-12] 영역 조회는 위도와 경도를 서로 바꿔 읽지 않는다")
    void areaDoesNotSwapLatitudeAndLongitude() {
        // given
        Member leader = memberFixture.save();
        long expected =
                insertBasecamp(leader, spotFixture.insert(aSpot().name("제자리").at(37.5, 37.6)), "RECRUITING", DAY, 4);
        insertBasecamp(leader, spotFixture.insert(aSpot().name("맞바꾼 자리").at(37.6, 37.5)), "RECRUITING", DAY, 4);
        BasecampSearchCondition.Area area = new BasecampSearchCondition.Area(37.4, 37.55, 37.55, 37.65);

        // when
        List<BasecampSearchRow> rows = mapper.selectRecruiting(areaCondition(area), 0, 20);

        // then
        assertThat(rows).extracting(BasecampSearchRow::basecampId).containsExactly(expected);
        assertThat(rows.getFirst().spotLat()).isEqualTo(37.5);
        assertThat(rows.getFirst().spotLng()).isEqualTo(37.6);
    }

    @Test
    @DisplayName("[F-12] 반경 검색은 반경 경계 안쪽 장소를 찾고 경계 바로 밖의 장소는 뺀다")
    void radiusIncludesInsideAndExcludesJustOutside() {
        // given
        Member leader = memberFixture.save();
        long center = insertBasecamp(
                leader, spotFixture.insert(aSpot().name("중심").at(CENTER.lat(), CENTER.lng())), "RECRUITING", DAY, 4);
        long north = insertBasecamp(
                leader,
                spotFixture.insert(aSpot().name("북쪽 9.8km").at(NORTH_9_8_KM.lat(), NORTH_9_8_KM.lng())),
                "RECRUITING",
                DAY,
                4);
        insertBasecamp(
                leader,
                spotFixture.insert(aSpot().name("동쪽 10.2km").at(EAST_10_2_KM.lat(), EAST_10_2_KM.lng())),
                "RECRUITING",
                DAY,
                4);

        // when
        List<BasecampSearchRow> rows = mapper.selectRecruiting(radiusCondition(CENTER, 10), 0, 20);

        // then
        assertThat(rows).extracting(BasecampSearchRow::basecampId).containsExactlyInAnyOrder(center, north);
    }

    @Test
    @DisplayName("[F-12] 출발일 범위는 양 끝을 포함하고, 한쪽만 보내면 그쪽만 제한한다")
    void startDateRange() {
        // given
        Member leader = memberFixture.save();
        long spotId = spotFixture.insert(aSpot().name("장소").at(37.5, 127.5));
        long d20 = insertBasecamp(leader, spotId, "RECRUITING", DAY, 4);
        long d22 = insertBasecamp(leader, spotId, "RECRUITING", DAY.plusDays(2), 4);
        long d25 = insertBasecamp(leader, spotId, "RECRUITING", DAY.plusDays(5), 4);

        // when
        List<BasecampSearchRow> between =
                mapper.selectRecruiting(condition(AREA, null, DAY.plusDays(2), DAY.plusDays(5), false), 0, 20);
        List<BasecampSearchRow> fromOnly =
                mapper.selectRecruiting(condition(AREA, null, DAY.plusDays(1), null, false), 0, 20);
        List<BasecampSearchRow> toOnly =
                mapper.selectRecruiting(condition(AREA, null, null, DAY.plusDays(2), false), 0, 20);

        // then
        assertThat(between).extracting(BasecampSearchRow::basecampId).containsExactly(d22, d25);
        assertThat(fromOnly).extracting(BasecampSearchRow::basecampId).containsExactly(d22, d25);
        assertThat(toOnly).extracting(BasecampSearchRow::basecampId).containsExactly(d20, d22);
    }

    @Test
    @DisplayName("[F-12] 남은 자리 조건은 ACTIVE 멤버 수만 세서 정원이 찬 베이스캠프를 뺀다")
    void hasVacancyCountsOnlyActiveMembers() {
        // given
        Member leader = memberFixture.save();
        Member other = memberFixture.save();
        Member leaver = memberFixture.save();
        long spotId = spotFixture.insert(aSpot().name("장소").at(37.5, 127.5));
        long full = insertBasecamp(leader, spotId, "RECRUITING", DAY, 2);
        insertMemberRow(full, other, "MEMBER", "ACTIVE");
        long vacant = insertBasecamp(leader, spotId, "RECRUITING", DAY, 2);
        insertMemberRow(vacant, leaver, "MEMBER", "LEFT");

        // when
        List<BasecampSearchRow> vacancyOnly = mapper.selectRecruiting(condition(AREA, null, null, null, true), 0, 20);
        List<BasecampSearchRow> all = mapper.selectRecruiting(condition(AREA, null, null, null, false), 0, 20);

        // then
        assertThat(vacancyOnly).extracting(BasecampSearchRow::basecampId).containsExactly(vacant);
        assertThat(all)
                .extracting(BasecampSearchRow::basecampId, BasecampSearchRow::headcount)
                .containsExactly(tuple(full, 2), tuple(vacant, 1));
    }

    @Test
    @DisplayName("[F-31] 장소 유형 조건은 고른 유형의 장소에서 열린 베이스캠프만 남긴다")
    void filtersBySpotType() {
        // given
        Member leader = memberFixture.save();
        long bakji = insertBasecamp(
                leader, spotFixture.insert(aSpot().name("박지").type("BAKJI").at(37.5, 127.5)), "RECRUITING", DAY, 4);
        long forest = insertBasecamp(
                leader, spotFixture.insert(aSpot().name("휴양림").type("FOREST").at(37.5, 127.5)), "RECRUITING", DAY, 4);
        insertBasecamp(
                leader, spotFixture.insert(aSpot().name("야영장").type("CAMPSITE").at(37.5, 127.5)), "RECRUITING", DAY, 4);

        // when
        List<BasecampSearchRow> rows = mapper.selectRecruiting(
                detailCondition(Set.of(BasecampSpotType.BAKJI, BasecampSpotType.FOREST), Set.of(), null, false, false),
                0,
                20);

        // then
        assertThat(rows).extracting(BasecampSearchRow::basecampId).containsExactly(bakji, forest);
    }

    @Test
    @DisplayName("[F-31] 박 수 조건은 종료일에서 출발일을 뺀 날 수가 고른 값인 베이스캠프만 남긴다")
    void filtersByNights() {
        // given
        Member leader = memberFixture.save();
        long spot = spotFixture.insert(aSpot().name("장소").at(37.5, 127.5));
        long oneNight = insertBasecamp(leader, spot, DAY, 1, null, null, null, false);
        insertBasecamp(leader, spot, DAY, 2, null, null, null, false);
        long threeNights = insertBasecamp(leader, spot, DAY, 3, null, null, null, false);

        // when
        List<BasecampSearchRow> rows =
                mapper.selectRecruiting(detailCondition(Set.of(), Set.of(1, 3), null, false, false), 0, 20);

        // then
        assertThat(rows).extracting(BasecampSearchRow::basecampId).containsExactly(oneNight, threeNights);
    }

    @Test
    @DisplayName("[F-31] 최소 신뢰 단계 상한은 조건이 없는 베이스캠프를 단계 1로 보고, 상한보다 높은 단계를 요구하는 베이스캠프를 뺀다")
    void filtersByMaxTrustLevel() {
        // given
        Member leader = memberFixture.save();
        long spot = spotFixture.insert(aSpot().name("장소").at(37.5, 127.5));
        long noCondition = insertBasecamp(leader, spot, DAY, 2, null, null, null, false);
        long levelOne = insertBasecamp(leader, spot, DAY, 2, 1, null, null, false);
        long levelTwo = insertBasecamp(leader, spot, DAY, 2, 2, null, null, false);

        // when
        List<BasecampSearchRow> upToOne =
                mapper.selectRecruiting(detailCondition(Set.of(), Set.of(), 1, false, false), 0, 20);
        List<BasecampSearchRow> upToTwo =
                mapper.selectRecruiting(detailCondition(Set.of(), Set.of(), 2, false, false), 0, 20);

        // then
        assertThat(upToOne).extracting(BasecampSearchRow::basecampId).containsExactly(noCondition, levelOne);
        assertThat(upToTwo).extracting(BasecampSearchRow::basecampId).containsExactly(noCondition, levelOne, levelTwo);
    }

    @Test
    @DisplayName("[F-31] 연령 제한 없음은 연령대 조건이 하나라도 있는 베이스캠프를 빼고, 동성만 아님은 동성만 받는 베이스캠프를 뺀다")
    void filtersByAgeLimitAndSameGender() {
        // given
        Member leader = memberFixture.save();
        long spot = spotFixture.insert(aSpot().name("장소").at(37.5, 127.5));
        long open = insertBasecamp(leader, spot, DAY, 2, null, null, null, false);
        long minAgeOnly = insertBasecamp(leader, spot, DAY, 2, null, 20, null, false);
        long maxAgeOnly = insertBasecamp(leader, spot, DAY, 2, null, null, 40, false);
        long sameGender = insertBasecamp(leader, spot, DAY, 2, null, null, null, true);

        // when
        List<BasecampSearchRow> noAgeLimit =
                mapper.selectRecruiting(detailCondition(Set.of(), Set.of(), null, true, false), 0, 20);
        List<BasecampSearchRow> noSameGender =
                mapper.selectRecruiting(detailCondition(Set.of(), Set.of(), null, false, true), 0, 20);
        List<BasecampSearchRow> both =
                mapper.selectRecruiting(detailCondition(Set.of(), Set.of(), null, true, true), 0, 20);

        // then
        assertThat(noAgeLimit).extracting(BasecampSearchRow::basecampId).containsExactly(open, sameGender);
        assertThat(noSameGender)
                .extracting(BasecampSearchRow::basecampId)
                .containsExactly(open, minAgeOnly, maxAgeOnly);
        assertThat(both).extracting(BasecampSearchRow::basecampId).containsExactly(open);
    }

    @Test
    @DisplayName("[F-31] 상세 조건을 함께 걸면 모두 만족하는 베이스캠프만 출발일 순서로 남긴다")
    void combinesDetailConditions() {
        // given
        Member leader = memberFixture.save();
        long bakji = spotFixture.insert(aSpot().name("박지").type("BAKJI").at(37.5, 127.5));
        long campsite = spotFixture.insert(aSpot().name("야영장").type("CAMPSITE").at(37.5, 127.5));
        long later = insertBasecamp(leader, bakji, DAY.plusDays(3), 1, 1, null, null, false);
        long earlier = insertBasecamp(leader, bakji, DAY, 1, null, null, null, false);
        insertBasecamp(leader, bakji, DAY, 2, null, null, null, false);
        insertBasecamp(leader, bakji, DAY, 1, 2, null, null, false);
        insertBasecamp(leader, bakji, DAY, 1, null, null, null, true);
        insertBasecamp(leader, campsite, DAY, 1, null, null, null, false);

        // when
        List<BasecampSearchRow> rows = mapper.selectRecruiting(
                detailCondition(Set.of(BasecampSpotType.BAKJI), Set.of(1), 1, false, true), 0, 20);

        // then
        assertThat(rows).extracting(BasecampSearchRow::basecampId).containsExactly(earlier, later);
    }

    @Test
    @DisplayName("[F-12] 모집 중인 베이스캠프만 찾고, 마감·확정·완료·취소와 숨긴 장소의 베이스캠프는 뺀다")
    void onlyRecruitingBasecampsOnActiveSpots() {
        // given
        Member leader = memberFixture.save();
        long spotId = spotFixture.insert(aSpot().name("장소").at(37.5, 127.5));
        long recruiting = insertBasecamp(leader, spotId, "RECRUITING", DAY, 4);
        for (BasecampStatus status : List.of(
                BasecampStatus.CLOSED, BasecampStatus.CONFIRMED, BasecampStatus.COMPLETED, BasecampStatus.CANCELED)) {
            insertBasecamp(leader, spotId, status.name(), DAY, 4);
        }
        long hiddenSpot = spotFixture.insert(aSpot().name("숨김 장소").at(37.5, 127.5));
        jdbc.update("UPDATE spot SET status = 'HIDDEN' WHERE id = ?", hiddenSpot);
        insertBasecamp(leader, hiddenSpot, "RECRUITING", DAY, 4);

        // when
        List<BasecampSearchRow> rows = mapper.selectRecruiting(areaCondition(AREA), 0, 20);

        // then
        assertThat(rows).extracting(BasecampSearchRow::basecampId).containsExactly(recruiting);
    }

    @Test
    @DisplayName("[F-12] 출발일 순서로 정렬하고 출발일이 같으면 ID 순서이며, offset과 limit으로 페이지를 나눈다")
    void orderedByStartDateThenIdWithPaging() {
        // given
        Member leader = memberFixture.save();
        long spotId = spotFixture.insert(aSpot().name("장소").at(37.5, 127.5));
        long late = insertBasecamp(leader, spotId, "RECRUITING", DAY.plusDays(3), 4);
        long earlyFirst = insertBasecamp(leader, spotId, "RECRUITING", DAY, 4);
        long earlySecond = insertBasecamp(leader, spotId, "RECRUITING", DAY, 4);

        // when
        List<BasecampSearchRow> all = mapper.selectRecruiting(areaCondition(AREA), 0, 20);
        List<BasecampSearchRow> secondPage = mapper.selectRecruiting(areaCondition(AREA), 2, 2);

        // then
        assertThat(all).extracting(BasecampSearchRow::basecampId).containsExactly(earlyFirst, earlySecond, late);
        assertThat(secondPage).extracting(BasecampSearchRow::basecampId).containsExactly(late);
    }

    @Test
    @DisplayName("[F-12] 결과에 장소 요약과 합류 조건, 정원, 현재 인원이 담긴다")
    void rowCarriesSpotAndJoinCondition() {
        // given
        Member leader = memberFixture.save();
        long spotId = spotFixture.insert(aSpot().name("능선 끝 평지").at(37.5, 127.5));
        long id = insertBasecamp(leader, spotId, "RECRUITING", DAY, 5);
        jdbc.update(
                "UPDATE basecamp SET min_trust_level = 2, age_group_min = 20, age_group_max = 30, same_gender_only = TRUE,"
                        + " required_gender = 'FEMALE' WHERE id = ?",
                id);

        // when
        BasecampSearchRow row =
                mapper.selectRecruiting(areaCondition(AREA), 0, 20).getFirst();

        // then
        assertThat(row.basecampId()).isEqualTo(id);
        assertThat(row.title()).isEqualTo("기존");
        assertThat(row.spotId()).isEqualTo(spotId);
        assertThat(row.spotName()).isEqualTo("능선 끝 평지");
        assertThat(row.spotType()).isEqualTo(BasecampSpotType.BAKJI);
        assertThat(row.spotLat()).isEqualTo(37.5);
        assertThat(row.spotLng()).isEqualTo(127.5);
        assertThat(row.startDate()).isEqualTo(DAY);
        assertThat(row.endDate()).isEqualTo(DAY.plusDays(2));
        assertThat(row.capacity()).isEqualTo(5);
        assertThat(row.headcount()).isEqualTo(1);
        assertThat(row.status()).isEqualTo(BasecampStatus.RECRUITING);
        assertThat(row.minTrustLevel()).isEqualTo(2);
        assertThat(row.ageGroupMin()).isEqualTo(20);
        assertThat(row.ageGroupMax()).isEqualTo(30);
        assertThat(row.sameGenderOnly()).isTrue();
        assertThat(row.requiredGender()).isEqualTo(Gender.FEMALE);
    }

    @Test
    @DisplayName("[F-12][BC-07] 회원의 멤버 행과 신청 행 상태를 베이스캠프별로 읽고, 둘 다 없는 베이스캠프는 읽지 않는다")
    void viewerHistoryReadsMemberAndApplicationStatus() {
        // given
        Member leader = memberFixture.save();
        Member viewer = memberFixture.save();
        long spotId = spotFixture.insert(aSpot().name("장소").at(37.5, 127.5));
        long asLeader = insertBasecamp(viewer, spotId, "RECRUITING", DAY, 4);
        long pending = insertBasecamp(leader, spotId, "RECRUITING", DAY, 4);
        insertApplicationRow(pending, viewer, "PENDING");
        long kicked = insertBasecamp(leader, spotId, "RECRUITING", DAY, 4);
        insertMemberRow(kicked, viewer, "MEMBER", "KICKED");
        insertApplicationRow(kicked, viewer, "APPROVED");
        long unrelated = insertBasecamp(leader, spotId, "RECRUITING", DAY, 4);

        // when
        List<ViewerHistoryRow> rows =
                mapper.selectViewerHistory(viewer.getId(), List.of(asLeader, pending, kicked, unrelated));

        // then
        assertThat(rows)
                .extracting(
                        ViewerHistoryRow::basecampId,
                        ViewerHistoryRow::memberStatus,
                        ViewerHistoryRow::applicationStatus)
                .containsExactlyInAnyOrder(
                        tuple(asLeader, BasecampMemberStatus.ACTIVE, null),
                        tuple(pending, null, BasecampApplicationStatus.PENDING),
                        tuple(kicked, BasecampMemberStatus.KICKED, BasecampApplicationStatus.APPROVED));
    }

    @Test
    @DisplayName("[F-12][BC-06] 회원이 ACTIVE 멤버로 들어 있는 모집 중·마감·확정 베이스캠프의 일정만 읽는다")
    void inProgressSchedulesOfActiveMembership() {
        // given
        Member leader = memberFixture.save();
        Member viewer = memberFixture.save();
        long spotId = spotFixture.insert(aSpot().name("장소").at(37.5, 127.5));
        long confirmedMember = insertBasecamp(leader, spotId, "CONFIRMED", DAY, 4);
        insertMemberRow(confirmedMember, viewer, "MEMBER", "ACTIVE");
        long confirmedLeft = insertBasecamp(leader, spotId, "CONFIRMED", DAY, 4);
        insertMemberRow(confirmedLeft, viewer, "MEMBER", "LEFT");
        long recruitingMember = insertBasecamp(leader, spotId, "RECRUITING", DAY, 4);
        insertMemberRow(recruitingMember, viewer, "MEMBER", "ACTIVE");
        long closedMember = insertBasecamp(leader, spotId, "CLOSED", DAY, 4);
        insertMemberRow(closedMember, viewer, "MEMBER", "ACTIVE");
        long completedMember = insertBasecamp(leader, spotId, "COMPLETED", DAY, 4);
        insertMemberRow(completedMember, viewer, "MEMBER", "ACTIVE");
        long canceledMember = insertBasecamp(leader, spotId, "CANCELED", DAY, 4);
        insertMemberRow(canceledMember, viewer, "MEMBER", "ACTIVE");
        insertBasecamp(leader, spotId, "CONFIRMED", DAY, 4);

        // when
        List<BasecampSchedule> rows = mapper.selectInProgressSchedules(viewer.getId());

        // then
        assertThat(rows)
                .containsExactlyInAnyOrder(
                        new BasecampSchedule(confirmedMember, DAY, DAY.plusDays(2)),
                        new BasecampSchedule(recruitingMember, DAY, DAY.plusDays(2)),
                        new BasecampSchedule(closedMember, DAY, DAY.plusDays(2)));
    }

    @Test
    @DisplayName("[F-12][BC-09] 장소 요약은 ACTIVE 장소의 이름·유형·좌표를 읽고, 없거나 숨김·검토 대기·삭제인 장소는 null이다")
    void spotSummary() {
        // given
        long spotId = spotFixture.insert(aSpot().name("능선 끝 평지").at(37.5, 127.25));
        long hiddenSpot = spotFixture.insert(aSpot().name("숨김 장소").at(37.5, 127.5));
        long pendingSpot = spotFixture.insert(aSpot().name("검토 대기 장소").at(37.5, 127.5));
        long deletedSpot = spotFixture.insert(aSpot().name("삭제 장소").at(37.5, 127.5));
        jdbc.update("UPDATE spot SET status = 'HIDDEN' WHERE id = ?", hiddenSpot);
        jdbc.update("UPDATE spot SET status = 'PENDING_REVIEW' WHERE id = ?", pendingSpot);
        jdbc.update("UPDATE spot SET status = 'DELETED' WHERE id = ?", deletedSpot);

        // when & then
        assertThat(mapper.selectSpotSummary(spotId))
                .isEqualTo(new SpotSummaryRow(spotId, "능선 끝 평지", BasecampSpotType.BAKJI, 37.5, 127.25));
        assertThat(mapper.selectSpotSummary(spotId + 1000)).isNull();
        assertThat(mapper.selectSpotSummary(hiddenSpot)).isNull();
        assertThat(mapper.selectSpotSummary(pendingSpot)).isNull();
        assertThat(mapper.selectSpotSummary(deletedSpot)).isNull();
    }

    private static BasecampSearchCondition condition(
            BasecampSearchCondition.Area area,
            BasecampSearchCondition.Nearby nearby,
            LocalDate fromDate,
            LocalDate toDate,
            boolean hasVacancy) {
        return new BasecampSearchCondition(
                area, nearby, fromDate, toDate, hasVacancy, Set.of(), Set.of(), null, BASE_TRUST_LEVEL, false, false);
    }

    private static BasecampSearchCondition detailCondition(
            Set<BasecampSpotType> spotTypes,
            Set<Integer> nights,
            Integer maxTrustLevel,
            boolean noAgeLimit,
            boolean noSameGenderOnly) {
        return new BasecampSearchCondition(
                AREA,
                null,
                null,
                null,
                false,
                spotTypes,
                nights,
                maxTrustLevel,
                BASE_TRUST_LEVEL,
                noAgeLimit,
                noSameGenderOnly);
    }

    private static BasecampSearchCondition areaCondition(BasecampSearchCondition.Area area) {
        return condition(area, null, null, null, false);
    }

    private static BasecampSearchCondition radiusCondition(Coordinate center, double radiusKm) {
        SpotSearchBox box = SpotSearchBox.around(center.lat(), center.lng(), radiusKm);
        BasecampSearchCondition.Nearby nearby = new BasecampSearchCondition.Nearby(
                center.lat(), center.lng(), radiusKm * 1000, box.swLat(), box.swLng(), box.neLat(), box.neLng());
        return condition(null, nearby, null, null, false);
    }

    private long insertBasecamp(Member leader, long spotId, String status, LocalDate startDate, int capacity) {
        jdbc.update(
                "INSERT INTO basecamp (leader_id, spot_id, title, description, start_date, end_date, capacity, status,"
                        + " created_at, updated_at) VALUES (?, ?, '기존', '기존', ?, ?, ?, ?,"
                        + " '2026-10-05 03:00:00', '2026-10-05 03:00:00')",
                leader.getId(),
                spotId,
                startDate,
                startDate.plusDays(2),
                capacity,
                status);
        long id = jdbc.queryForObject("SELECT MAX(id) FROM basecamp", Long.class);
        insertMemberRow(id, leader, "LEADER", "ACTIVE");
        return id;
    }

    private long insertBasecamp(
            Member leader,
            long spotId,
            LocalDate startDate,
            int nights,
            Integer minTrustLevel,
            Integer ageGroupMin,
            Integer ageGroupMax,
            boolean sameGenderOnly) {
        jdbc.update(
                "INSERT INTO basecamp (leader_id, spot_id, title, description, start_date, end_date, capacity, status,"
                        + " min_trust_level, age_group_min, age_group_max, same_gender_only, required_gender,"
                        + " created_at, updated_at) VALUES (?, ?, '기존', '기존', ?, ?, 4, 'RECRUITING', ?, ?, ?, ?, ?,"
                        + " '2026-10-05 03:00:00', '2026-10-05 03:00:00')",
                leader.getId(),
                spotId,
                startDate,
                startDate.plusDays(nights),
                minTrustLevel,
                ageGroupMin,
                ageGroupMax,
                sameGenderOnly,
                sameGenderOnly ? "FEMALE" : null);
        long id = jdbc.queryForObject("SELECT MAX(id) FROM basecamp", Long.class);
        insertMemberRow(id, leader, "LEADER", "ACTIVE");
        return id;
    }

    private void insertMemberRow(long basecampId, Member member, String role, String status) {
        jdbc.update(
                "INSERT INTO basecamp_member (basecamp_id, member_id, role, status, joined_at, created_at, updated_at)"
                        + " VALUES (?, ?, ?, ?, '2026-10-05 03:00:00', '2026-10-05 03:00:00', '2026-10-05 03:00:00')",
                basecampId,
                member.getId(),
                role,
                status);
    }

    private void insertApplicationRow(long basecampId, Member applicant, String status) {
        jdbc.update(
                "INSERT INTO basecamp_application (basecamp_id, applicant_id, status, applied_at, created_at,"
                        + " updated_at) VALUES (?, ?, ?, '2026-10-05 03:00:00', '2026-10-05 03:00:00',"
                        + " '2026-10-05 03:00:00')",
                basecampId,
                applicant.getId(),
                status);
    }

    private record Coordinate(double lat, double lng) {}
}
