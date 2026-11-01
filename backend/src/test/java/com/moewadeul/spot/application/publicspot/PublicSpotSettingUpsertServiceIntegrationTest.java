package com.moewadeul.spot.application.publicspot;

import static com.moewadeul.spot.application.publicspot.PublicSpotSyncFixture.campsiteWithSettings;
import static org.assertj.core.api.Assertions.assertThat;

import com.moewadeul.common.testsupport.IntegrationTest;
import com.moewadeul.spot.application.PublicSpotSetting;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

@IntegrationTest
class PublicSpotSettingUpsertServiceIntegrationTest {

    @Autowired
    private PublicSpotSyncService publicSpotSyncService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private PublicSpotSyncFixture fixture;

    @BeforeEach
    void setUp() {
        fixture = new PublicSpotSyncFixture(jdbcTemplate);
    }

    @Test
    @DisplayName("[F-31] 새 야영장의 입지를 public_spot_setting에 저장하고, 입지가 없는 야영장은 행을 만들지 않는다")
    void insertsSettingsOfNewCampsite() {
        // given
        PublicSpotCommand withSettings =
                campsiteWithSettings("100", Set.of(PublicSpotSetting.MOUNTAIN, PublicSpotSetting.VALLEY));
        PublicSpotCommand withoutSettings = campsiteWithSettings("101", Set.of());

        // when
        PublicSpotUpsertResult result =
                publicSpotSyncService.upsert(PublicSpotSource.GOCAMPING, List.of(withSettings, withoutSettings));

        // then
        assertThat(result).isEqualTo(new PublicSpotUpsertResult(2, 0, 0, 0));
        assertThat(fixture.findSettings("100")).containsExactlyInAnyOrder("MOUNTAIN", "VALLEY");
        assertThat(fixture.findSettings("101")).isEmpty();
    }

    @Test
    @DisplayName("[F-31] 입지가 그대로인 야영장을 다시 적재하면 바뀌지 않은 것으로 센다")
    void unchangedSettingsAreNotUpdated() {
        // given
        PublicSpotCommand command = campsiteWithSettings("100", Set.of(PublicSpotSetting.RIVER));
        publicSpotSyncService.upsert(PublicSpotSource.GOCAMPING, List.of(command));

        // when
        PublicSpotUpsertResult result = publicSpotSyncService.upsert(PublicSpotSource.GOCAMPING, List.of(command));

        // then
        assertThat(result).isEqualTo(new PublicSpotUpsertResult(0, 0, 1, 0));
        assertThat(fixture.findSettings("100")).containsExactly("RIVER");
    }

    @Test
    @DisplayName("[F-31] 입지가 바뀐 야영장은 입지 행을 새 값으로 바꾸고 갱신 한 건으로 센다")
    void changedSettingsReplaceRowsAndCountAsOneUpdate() {
        // given
        publicSpotSyncService.upsert(
                PublicSpotSource.GOCAMPING,
                List.of(campsiteWithSettings("100", Set.of(PublicSpotSetting.MOUNTAIN, PublicSpotSetting.VALLEY))));
        PublicSpotCommand changed =
                campsiteWithSettings("100", Set.of(PublicSpotSetting.VALLEY, PublicSpotSetting.LAKE));

        // when
        PublicSpotUpsertResult result = publicSpotSyncService.upsert(PublicSpotSource.GOCAMPING, List.of(changed));

        // then
        assertThat(result).isEqualTo(new PublicSpotUpsertResult(0, 1, 0, 0));
        assertThat(fixture.findSettings("100")).containsExactlyInAnyOrder("VALLEY", "LAKE");
    }

    @Test
    @DisplayName("[F-31] 입지가 비게 된 야영장은 입지 행을 모두 지운다")
    void emptiedSettingsDeleteRows() {
        // given
        publicSpotSyncService.upsert(
                PublicSpotSource.GOCAMPING, List.of(campsiteWithSettings("100", Set.of(PublicSpotSetting.BEACH))));

        // when
        PublicSpotUpsertResult result = publicSpotSyncService.upsert(
                PublicSpotSource.GOCAMPING, List.of(campsiteWithSettings("100", Set.of())));

        // then
        assertThat(result).isEqualTo(new PublicSpotUpsertResult(0, 1, 0, 0));
        assertThat(fixture.findSettings("100")).isEmpty();
    }
}
