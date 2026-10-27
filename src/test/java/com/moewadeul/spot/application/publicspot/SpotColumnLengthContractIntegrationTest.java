package com.moewadeul.spot.application.publicspot;

import static org.assertj.core.api.Assertions.assertThat;

import com.moewadeul.common.testsupport.IntegrationTest;
import com.moewadeul.spot.domain.Spot;
import com.moewadeul.spot.domain.bakji.BakjiContent;
import com.moewadeul.spot.domain.bakji.BakjiReport;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

@IntegrationTest
class SpotColumnLengthContractIntegrationTest {

    @Autowired
    private JdbcTemplate jdbc;

    static Stream<Arguments> lengthConstantsAndTheirColumns() {
        return Stream.of(
                Arguments.of(
                        "NormalizedPublicSpot.MAX_EXTERNAL_ID_LENGTH",
                        NormalizedPublicSpot.MAX_EXTERNAL_ID_LENGTH,
                        "public_spot_detail",
                        "external_id"),
                Arguments.of(
                        "NormalizedPublicSpot.MAX_NAME_LENGTH", NormalizedPublicSpot.MAX_NAME_LENGTH, "spot", "name"),
                Arguments.of(
                        "NormalizedPublicSpot.MAX_ADDRESS_LENGTH",
                        NormalizedPublicSpot.MAX_ADDRESS_LENGTH,
                        "spot",
                        "address"),
                Arguments.of(
                        "NormalizedPublicSpot.MAX_CATEGORY_LENGTH",
                        NormalizedPublicSpot.MAX_CATEGORY_LENGTH,
                        "public_spot_detail",
                        "category"),
                Arguments.of(
                        "NormalizedPublicSpot.MAX_PHONE_LENGTH",
                        NormalizedPublicSpot.MAX_PHONE_LENGTH,
                        "public_spot_detail",
                        "phone"),
                Arguments.of(
                        "NormalizedPublicSpot.MAX_HOMEPAGE_LENGTH",
                        NormalizedPublicSpot.MAX_HOMEPAGE_LENGTH,
                        "public_spot_detail",
                        "homepage"),
                Arguments.of("Spot.NAME_MAX_LENGTH", Spot.NAME_MAX_LENGTH, "spot", "name"),
                Arguments.of(
                        "BakjiContent.DESCRIPTION_MAX_LENGTH",
                        BakjiContent.DESCRIPTION_MAX_LENGTH,
                        "bakji_detail",
                        "description"),
                Arguments.of(
                        "BakjiReport.CONTENT_MAX_LENGTH", BakjiReport.CONTENT_MAX_LENGTH, "bakji_report", "content"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("lengthConstantsAndTheirColumns")
    @DisplayName("자바 길이 상수는 마이그레이션이 만든 컬럼 길이와 같다")
    void constantEqualsColumnLength(String constant, int length, String table, String column) {
        // when
        Integer columnLength = jdbc.queryForObject(
                "SELECT CHARACTER_MAXIMUM_LENGTH FROM information_schema.COLUMNS"
                        + " WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? AND COLUMN_NAME = ?",
                Integer.class,
                table,
                column);

        // then
        assertThat(length).isEqualTo(columnLength);
    }
}
