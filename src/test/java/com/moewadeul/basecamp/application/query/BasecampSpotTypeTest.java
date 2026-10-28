package com.moewadeul.basecamp.application.query;

import static org.assertj.core.api.Assertions.assertThat;

import com.moewadeul.spot.domain.SpotType;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class BasecampSpotTypeTest {

    @Test
    @DisplayName("베이스캠프가 따로 두는 장소 유형은 spot의 장소 유형과 이름이 같다")
    void namesMatchSpotType() {
        // given
        List<String> spotTypes =
                Arrays.stream(SpotType.values()).map(Enum::name).toList();

        // when
        List<String> basecampSpotTypes =
                Arrays.stream(BasecampSpotType.values()).map(Enum::name).toList();

        // then
        assertThat(basecampSpotTypes).containsExactlyInAnyOrderElementsOf(spotTypes);
    }
}
