package com.moewadeul.publicdata.application.forest;

import static org.assertj.core.api.Assertions.assertThat;

import com.moewadeul.publicdata.application.forest.ForestStandardMatcher.Forest;
import com.moewadeul.publicdata.application.forest.ForestStandardMatcher.Result;
import com.moewadeul.publicdata.infra.forest.ForestStandardRecord;
import com.moewadeul.spot.application.publicspot.PublicSpotLocation;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ForestStandardMatcherTest {

    private static final double LAT = 37.5;
    private static final double LNG = 127.5;
    private static final List<PublicSpotLocation> NONE = List.of();

    @Test
    @DisplayName("[F-06] 100m 안의 행을 한 휴양림으로 묶고, 기준일이 가장 늦은 행을 대표로 삼는다")
    void groupsRowsWithin100MetersAndPicksLatestAsRepresentative() {
        // given
        List<ForestStandardRecord> records = List.of(
                row("가나 자연휴양림", "Y", LAT, LNG, "2026-09-01"),
                row("가나자연휴양림(시립)", "Y", LAT + 0.0005, LNG, "2026-09-10"),
                row("가나 휴양림", "Y", LAT + 0.001, LNG, "2026-09-05"));

        // when
        Result result = ForestStandardMatcher.match(records, NONE, NONE);

        // then
        assertThat(result.forests()).singleElement().satisfies(forest -> {
            assertThat(forest.rowCount()).isEqualTo(3);
            assertThat(forest.representative().record().name()).isEqualTo("가나자연휴양림(시립)");
        });
        assertThat(result.groupCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("[F-06] 기준일이 같으면 파일에서 먼저 나온 행을 대표로 삼는다")
    void picksFirstRowWhenSourceDatesTie() {
        // given
        List<ForestStandardRecord> records =
                List.of(row("가나 자연휴양림", "Y", LAT, LNG, "2026-09-01"), row("가나자연휴양림(시립)", "Y", LAT, LNG, "2026-09-01"));

        // when
        Result result = ForestStandardMatcher.match(records, NONE, NONE);

        // then
        assertThat(result.forests().get(0).representative().record().name()).isEqualTo("가나 자연휴양림");
    }

    @Test
    @DisplayName("[F-06] 100m보다 떨어진 행은 같은 이름이어도 따로 둔다")
    void keepsRowsApartWhenFartherThan100Meters() {
        // given
        List<ForestStandardRecord> records = List.of(
                row("가나 자연휴양림", "Y", LAT, LNG, "2026-09-01"), row("가나 자연휴양림", "Y", LAT + 0.002, LNG, "2026-09-01"));

        // when
        Result result = ForestStandardMatcher.match(records, NONE, NONE);

        // then
        assertThat(result.forests()).hasSize(2);
    }

    @Test
    @DisplayName("[F-06] 숙박가능여부가 Y가 아닌 행과 위경도를 읽지 못하는 행은 빼고 각각 센다")
    void excludesNoLodgingAndInvalidRows() {
        // given
        List<ForestStandardRecord> records = List.of(
                row("가나 자연휴양림", "Y", LAT, LNG, "2026-09-01"),
                row("다라 자연휴양림", "N", LAT + 1, LNG, "2026-09-01"),
                new ForestStandardRecord("마바 자연휴양림", "", "Y", "", "", "", "abc", "127.5", "2026-09-01"),
                new ForestStandardRecord("사아 자연휴양림", "", "Y", "", "", "", "", "", "2026-09-01"),
                new ForestStandardRecord("", "", "Y", "", "", "", "36.5", "127.5", "2026-09-01"));

        // when
        Result result = ForestStandardMatcher.match(records, NONE, NONE);

        // then
        assertThat(result.forests()).hasSize(1);
        assertThat(result.skippedNoLodging()).isEqualTo(1);
        assertThat(result.skippedInvalid()).isEqualTo(3);
    }

    @Test
    @DisplayName("[F-06] 국립 휴양림과 100m 안이면 이름이 달라도 국립과 겹친 것으로 보고 뺀다")
    void skipsForestWithin100MetersOfNational() {
        // given
        List<ForestStandardRecord> records = List.of(row("전혀 다른 이름", "Y", LAT, LNG, "2026-09-01"));
        List<PublicSpotLocation> national = List.of(location("0101", "유명산 자연휴양림", LAT + 0.0005, LNG));

        // when
        Result result = ForestStandardMatcher.match(records, national, NONE);

        // then
        assertThat(result.forests()).isEmpty();
        assertThat(result.groupCount()).isEqualTo(1);
        assertThat(result.skippedNational()).isEqualTo(1);
    }

    @Test
    @DisplayName("[F-06] 이름 핵심이 같거나 서로 포함하고 1.5km 안이면 국립과 겹친 것으로 보고 뺀다")
    void skipsForestWithSameNameCoreWithin1500Meters() {
        // given
        List<ForestStandardRecord> records = List.of(row("유명 휴양림(도립)", "Y", LAT + 0.009, LNG, "2026-09-01"));
        List<PublicSpotLocation> national = List.of(location("0101", "국립 유명산 자연휴양림", LAT, LNG));

        // when
        Result result = ForestStandardMatcher.match(records, national, NONE);

        // then
        assertThat(result.forests()).isEmpty();
        assertThat(result.skippedNational()).isEqualTo(1);
    }

    @Test
    @DisplayName("[F-06] 이름이 같아도 1.5km보다 멀면 국립과 겹친 것으로 보지 않고 둔다")
    void keepsForestWithSameNameButFarFromNational() {
        // given
        List<ForestStandardRecord> records = List.of(row("유명산 휴양림", "Y", LAT + 0.027, LNG, "2026-09-01"));
        List<PublicSpotLocation> national = List.of(location("0101", "유명산 자연휴양림", LAT, LNG));

        // when
        Result result = ForestStandardMatcher.match(records, national, NONE);

        // then
        assertThat(result.forests()).hasSize(1);
        assertThat(result.skippedNational()).isZero();
    }

    @Test
    @DisplayName("[F-06] 이름 핵심이 2자 미만이면 이름으로는 겹침을 판정하지 않는다")
    void doesNotCompareNamesShorterThanTwoCharacters() {
        // given
        List<ForestStandardRecord> records = List.of(row("숲 휴양림", "Y", LAT + 0.009, LNG, "2026-09-01"));
        List<PublicSpotLocation> national = List.of(location("0101", "국립 숲 자연휴양림", LAT, LNG));

        // when
        Result result = ForestStandardMatcher.match(records, national, NONE);

        // then
        assertThat(result.forests()).hasSize(1);
    }

    @Test
    @DisplayName("[F-06] 묶음의 대표가 아닌 행이 국립과 겹쳐도 묶음 전체를 뺀다")
    void skipsGroupWhenAnyRowOverlapsNational() {
        // given
        List<ForestStandardRecord> records = List.of(
                row("국립과 같은 자리", "Y", LAT, LNG, "2026-09-01"), row("대표 행", "Y", LAT + 0.0007, LNG, "2026-09-10"));
        List<PublicSpotLocation> national = List.of(location("0101", "전혀 다른 국립", LAT - 0.0004, LNG));

        // when
        Result result = ForestStandardMatcher.match(records, national, NONE);

        // then
        assertThat(result.forests()).isEmpty();
        assertThat(result.skippedNational()).isEqualTo(1);
    }

    @Test
    @DisplayName("[F-06] 이름을 바꿔도 100m 안의 기존 장소가 있으면 그 외부 ID를 이어 쓴다")
    void reusesExternalIdAfterRename() {
        // given
        List<ForestStandardRecord> records = List.of(row("새 이름 휴양림", "Y", LAT, LNG, "2026-09-01"));
        List<PublicSpotLocation> existing = List.of(location("RF-old", "옛 이름 휴양림", LAT + 0.0003, LNG));

        // when
        Result result = ForestStandardMatcher.match(records, NONE, existing);

        // then
        assertThat(externalIds(result)).containsExactly("RF-old");
    }

    @Test
    @DisplayName("[F-06] 좌표를 200m 고쳐도 이름 핵심이 같은 기존 장소가 있으면 그 외부 ID를 이어 쓴다")
    void reusesExternalIdAfterCoordinateFixWithSameName() {
        // given
        List<ForestStandardRecord> records = List.of(row("가나 자연휴양림", "Y", LAT + 0.002, LNG, "2026-09-01"));
        List<PublicSpotLocation> existing = List.of(location("RF-old", "가나 휴양림", LAT, LNG));

        // when
        Result result = ForestStandardMatcher.match(records, NONE, existing);

        // then
        assertThat(externalIds(result)).containsExactly("RF-old");
    }

    @Test
    @DisplayName("[F-06] 100m보다 멀고 이름 핵심도 다른 기존 장소와는 이어 쓰지 않고 새 외부 ID를 만든다")
    void createsNewExternalIdWhenExistingIsFarAndDifferentName() {
        // given
        List<ForestStandardRecord> records = List.of(row("가나 자연휴양림", "Y", LAT + 0.002, LNG, "2026-09-01"));
        List<PublicSpotLocation> existing = List.of(location("RF-old", "다라 휴양림", LAT, LNG));

        // when
        Result result = ForestStandardMatcher.match(records, NONE, existing);

        // then
        assertThat(externalIds(result))
                .singleElement()
                .asString()
                .startsWith("RF-")
                .isNotEqualTo("RF-old");
    }

    @Test
    @DisplayName("[F-06] 기존 장소 하나를 두 휴양림이 다투면 더 가까운 휴양림만 그 외부 ID를 가져간다")
    void givesExistingLocationToNearestForestOnly() {
        // given
        List<ForestStandardRecord> records = List.of(
                row("가나 자연휴양림", "Y", LAT, LNG, "2026-09-01"), row("다라 자연휴양림", "Y", LAT + 0.0015, LNG, "2026-09-01"));
        List<PublicSpotLocation> existing = List.of(location("RF-old", "마바 휴양림", LAT + 0.0007, LNG));

        // when
        Result result = ForestStandardMatcher.match(records, NONE, existing);

        // then
        assertThat(result.forests().get(0).externalId()).isEqualTo("RF-old");
        assertThat(result.forests().get(1).externalId()).isNotEqualTo("RF-old");
    }

    @Test
    @DisplayName("[F-06] 새 외부 ID는 RF-와 해시 24자이고, 같은 입력이면 늘 같으며 이름 핵심이나 좌표가 다르면 달라진다")
    void newExternalIdIsDeterministic() {
        // given
        List<ForestStandardRecord> records = List.of(row("가나 자연휴양림", "Y", LAT, LNG, "2026-09-01"));
        List<ForestStandardRecord> sameWithAnotherDate = List.of(row("가나 휴양림", "Y", LAT, LNG, "2026-12-01"));
        List<ForestStandardRecord> movedRecords = List.of(row("가나 자연휴양림", "Y", LAT + 0.01, LNG, "2026-09-01"));
        List<ForestStandardRecord> renamedRecords = List.of(row("다라 자연휴양림", "Y", LAT, LNG, "2026-09-01"));

        // when
        String first =
                externalIds(ForestStandardMatcher.match(records, NONE, NONE)).get(0);

        // then
        assertThat(first).matches("RF-[0-9a-f]{24}");
        assertThat(externalIds(ForestStandardMatcher.match(records, NONE, NONE)))
                .containsExactly(first);
        assertThat(externalIds(ForestStandardMatcher.match(sameWithAnotherDate, NONE, NONE)))
                .containsExactly(first);
        assertThat(externalIds(ForestStandardMatcher.match(movedRecords, NONE, NONE)))
                .doesNotContain(first);
        assertThat(externalIds(ForestStandardMatcher.match(renamedRecords, NONE, NONE)))
                .doesNotContain(first);
    }

    @Test
    @DisplayName("[F-06] 새 외부 ID가 이어 붙이지 못한 기존 장소의 외부 ID와 겹치면 -2를 붙인다")
    void addsSuffixWhenNewIdCollidesWithUnmatchedExistingId() {
        // given
        List<ForestStandardRecord> records = List.of(row("가나 자연휴양림", "Y", LAT, LNG, "2026-09-01"));
        String base =
                externalIds(ForestStandardMatcher.match(records, NONE, NONE)).get(0);
        List<PublicSpotLocation> existing = List.of(location(base, "멀리 있는 다른 휴양림", LAT + 1, LNG));

        // when
        Result result = ForestStandardMatcher.match(records, NONE, existing);

        // then
        assertThat(externalIds(result)).containsExactly(base + "-2");
    }

    @Test
    @DisplayName("[F-06] 이름 핵심은 괄호 내용, 공백, 국립, 자연휴양림, 휴양림을 지운 문자열이다")
    void nameCoreRemovesDecorations() {
        assertThat(ForestStandardMatcher.nameCore("국립 유명산 자연휴양림")).isEqualTo("유명산");
        assertThat(ForestStandardMatcher.nameCore("유명산휴양림(경기도 가평군)")).isEqualTo("유명산");
        assertThat(ForestStandardMatcher.nameCore("유명산 자연 숲")).isEqualTo("유명산자연숲");
        assertThat(ForestStandardMatcher.nameCore("가나 숲 (시립")).isEqualTo("가나숲");
    }

    private static List<String> externalIds(Result result) {
        return result.forests().stream().map(Forest::externalId).toList();
    }

    private static ForestStandardRecord row(String name, String lodging, double lat, double lng, String date) {
        return new ForestStandardRecord(
                name,
                "경기도",
                lodging,
                "주소",
                "031-000-0000",
                "https://example.com",
                String.valueOf(lat),
                String.valueOf(lng),
                date);
    }

    private static PublicSpotLocation location(String externalId, String name, double lat, double lng) {
        return new PublicSpotLocation(externalId, name, lat, lng);
    }
}
