package com.moewadeul.publicdata.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.moewadeul.spot.application.publicspot.PublicSpotSource;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SyncJobTypeTest {

    @Test
    @DisplayName("공공 장소 출처는 모두 동기화 작업 유형에 있어 상세 조회의 job_type 조인이 어긋나지 않는다")
    void containsEveryPublicSpotSource() {
        // given
        List<String> sources =
                Arrays.stream(PublicSpotSource.values()).map(Enum::name).toList();

        // when
        List<String> jobTypes =
                Arrays.stream(SyncJobType.values()).map(Enum::name).toList();

        // then
        assertThat(jobTypes).containsAll(sources);
    }
}
