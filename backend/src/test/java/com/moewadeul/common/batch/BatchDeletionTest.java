package com.moewadeul.common.batch;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.LongStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class BatchDeletionTest {

    @Test
    @DisplayName("[NFR-09] id를 고른 뒤 갱신하는 배치는 대상이 배치 크기보다 많으면 여러 번 돌고, 마지막 배치가 차지 않으면 멈춘다")
    void runsUntilBatchIsNotFull() {
        List<Long> pending =
                new ArrayList<>(LongStream.rangeClosed(1, 7).boxed().toList());
        List<Integer> batchSizes = new ArrayList<>();

        int total = BatchDeletion.untilDrainedByIds(
                3,
                size -> pending.subList(0, Math.min(size, pending.size())).stream()
                        .toList(),
                ids -> {
                    batchSizes.add(ids.size());
                    pending.removeAll(ids);
                    return ids.size();
                });

        assertThat(total).isEqualTo(7);
        assertThat(batchSizes).containsExactly(3, 3, 1);
        assertThat(pending).isEmpty();
    }

    @Test
    @DisplayName("[NFR-09] 고를 대상이 없으면 갱신하지 않고 0을 돌려준다")
    void doesNotUpdateWhenNothingSelected() {
        int total = BatchDeletion.untilDrainedByIds(3, size -> List.of(), ids -> {
            throw new AssertionError("갱신하면 안 된다");
        });

        assertThat(total).isZero();
    }

    @Test
    @DisplayName("[NFR-09] 고른 id가 모두 그사이 조건에서 벗어나 한 건도 못 바꾸면 같은 id를 되풀이하지 않고 멈춘다")
    void stopsWhenNothingWasUpdated() {
        List<Integer> calls = new ArrayList<>();

        int total = BatchDeletion.untilDrainedByIds(2, size -> List.of(1L, 2L), ids -> {
            calls.add(ids.size());
            return 0;
        });

        assertThat(total).isZero();
        assertThat(calls).hasSize(1);
    }
}
