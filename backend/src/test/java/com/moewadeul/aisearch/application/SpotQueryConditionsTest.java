package com.moewadeul.aisearch.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SpotQueryConditionsTest {

    @Test
    @DisplayName("[F-27] 반경이 50km를 넘으면 50km로 줄인다")
    void clampsRadius() {
        assertThat(conditions("설악산", 80.0).radiusKm()).isEqualTo(50.0);
        assertThat(conditions("설악산", 50.0).radiusKm()).isEqualTo(50.0);
        assertThat(conditions("설악산", 12.5).radiusKm()).isEqualTo(12.5);
    }

    @Test
    @DisplayName("[F-27] 반경이 0 이하이면 만들 수 없다")
    void rejectsNonPositiveRadius() {
        assertThatThrownBy(() -> conditions("설악산", 0.0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> conditions("설악산", -1.0)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("[F-27] 컬렉션이 null이면 빈 컬렉션으로 둔다")
    void nullCollectionsBecomeEmpty() {
        SpotQueryConditions conditions =
                new SpotQueryConditions(null, null, null, false, false, false, false, null, null);

        assertThat(conditions.types()).isEmpty();
        assertThat(conditions.settings()).isEmpty();
        assertThat(conditions.unparsed()).isEmpty();
    }

    @Test
    @DisplayName("[F-27] 공백뿐인 지역은 지역 없음으로 둔다")
    void blankRegionBecomesNull() {
        assertThat(conditions("  ", null).region()).isNull();
    }

    private static SpotQueryConditions conditions(String region, Double radiusKm) {
        return new SpotQueryConditions(
                region, radiusKm, Set.of(AiSpotType.CAMPSITE), false, false, false, false, Set.of(), List.of());
    }
}
