package com.moewadeul.memory.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.moewadeul.common.error.BusinessException;
import com.moewadeul.common.error.CommonErrorCode;
import com.moewadeul.common.testsupport.MutableClock;
import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class MemoryTest {

    private static final Instant NOW = MutableClock.DEFAULT_INSTANT;
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 5);

    @ParameterizedTest
    @ValueSource(ints = {0, 30})
    @DisplayName("[F-33][MM-02] 박 수는 0박과 30박을 받는다")
    void acceptsNightsBoundary(int nights) {
        MemoryDetails details = details(nights, null);

        assertThat(details.nights()).isEqualTo(nights);
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, 31})
    @DisplayName("[F-33][MM-02] 박 수가 -1이거나 31이면 내용을 만들지 않는다")
    void rejectsNightsOutOfRange(int nights) {
        assertThatThrownBy(() -> details(nights, null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("[F-33][MM-02] 글은 없어도 되고, 2,000자까지 받으며 공백뿐이거나 2,001자이면 받지 않는다")
    void contentIsOptionalButBounded() {
        assertThat(details(1, null).content()).isNull();
        assertThat(details(1, "가".repeat(MemoryDetails.CONTENT_MAX_LENGTH)).content())
                .hasSize(MemoryDetails.CONTENT_MAX_LENGTH);
        assertThatThrownBy(() -> details(1, "   ")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> details(1, "가".repeat(MemoryDetails.CONTENT_MAX_LENGTH + 1)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("[F-33][MM-02] 위도가 90을, 경도가 180을 넘으면 내용을 만들지 않는다")
    void rejectsCoordinatesOutOfRange() {
        assertThatThrownBy(() -> new MemoryDetails(null, 90.000001, 127.0, TODAY, 1, null, MemoryVisibility.PRIVATE))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new MemoryDetails(null, 37.0, -180.000001, TODAY, 1, null, MemoryVisibility.PRIVATE))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("[F-33][MM-02] 오늘 방문한 기록은 미래가 아니고, 내일 방문했다고 적은 기록은 미래다")
    void visitedAfterToday() {
        assertThat(details(1, null).isVisitedAfter(TODAY)).isFalse();
        assertThat(new MemoryDetails(null, 37.0, 127.0, TODAY.plusDays(1), 1, null, MemoryVisibility.PRIVATE)
                        .isVisitedAfter(TODAY))
                .isTrue();
    }

    @Test
    @DisplayName("[F-33][MM-04] 작성자가 고치면 내용을 통째로 바꾸고 수정 시각을 남기며, 작성자만 작성자로 본다")
    void reviseReplacesDetails() {
        Memory memory = Memory.write(1L, details(1, "처음"), NOW);
        MemoryDetails changed =
                new MemoryDetails(9L, 35.1, 129.0, TODAY.minusDays(3), 2, null, MemoryVisibility.PUBLIC);

        memory.revise(changed, NOW.plusSeconds(60));

        assertThat(memory.details()).isEqualTo(changed);
        assertThat(memory.getUpdatedAt()).isEqualTo(NOW.plusSeconds(60));
        assertThat(memory.getCreatedAt()).isEqualTo(NOW);
        assertThatCode(() -> memory.requireWrittenBy(1L)).doesNotThrowAnyException();
        assertThatThrownBy(() -> memory.requireWrittenBy(2L))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(CommonErrorCode.ACCESS_DENIED));
    }

    @Test
    @DisplayName("[F-33][MM-10] 새 기록은 ACTIVE로 시작하고, 공개이면서 ACTIVE일 때만 다른 사람에게 보인다")
    void newMemoryIsActiveAndVisibleOnlyWhenPublic() {
        Memory privateMemory = Memory.write(1L, details(1, null), NOW);
        Memory publicMemory = Memory.write(1L, publicDetails(), NOW);

        assertThat(privateMemory.getStatus()).isEqualTo(MemoryStatus.ACTIVE);
        assertThat(privateMemory.isVisibleToOthers()).isFalse();
        assertThat(publicMemory.isVisibleToOthers()).isTrue();

        publicMemory.markPendingReview(NOW.plusSeconds(60));

        assertThat(publicMemory.getStatus()).isEqualTo(MemoryStatus.PENDING_REVIEW);
        assertThat(publicMemory.isVisibleToOthers()).isFalse();
        assertThat(publicMemory.getUpdatedAt()).isEqualTo(NOW.plusSeconds(60));
    }

    @Test
    @DisplayName("[F-33][MM-10] 검토 대기로는 ACTIVE에서만 바꾸고, 이미 검토 대기이거나 숨긴 기록이면 거부한다")
    void markPendingReviewOnlyFromActive() {
        Memory pending = Memory.write(1L, publicDetails(), NOW);
        pending.markPendingReview(NOW);
        Memory hidden = Memory.write(1L, publicDetails(), NOW);
        hidden.hide(NOW);

        assertThatThrownBy(() -> pending.markPendingReview(NOW)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> hidden.markPendingReview(NOW)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("[F-33][MM-11] 관리자는 ACTIVE와 검토 대기 기록을 숨기고, 이미 숨긴 기록을 숨기면 MEMORY_INVALID_STATE다")
    void hideFromActiveOrPendingReview() {
        Memory active = Memory.write(1L, publicDetails(), NOW);
        Memory pending = Memory.write(1L, publicDetails(), NOW);
        pending.markPendingReview(NOW);

        active.hide(NOW.plusSeconds(60));
        pending.hide(NOW.plusSeconds(60));

        assertThat(active.getStatus()).isEqualTo(MemoryStatus.HIDDEN);
        assertThat(pending.getStatus()).isEqualTo(MemoryStatus.HIDDEN);
        assertThat(active.getUpdatedAt()).isEqualTo(NOW.plusSeconds(60));
        assertThatThrownBy(() -> active.hide(NOW))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(MemoryErrorCode.MEMORY_INVALID_STATE));
    }

    @Test
    @DisplayName("[F-33][MM-11] 관리자는 검토 대기와 숨긴 기록을 ACTIVE로 복구하고, ACTIVE 기록을 복구하면 MEMORY_INVALID_STATE다")
    void restoreFromPendingReviewOrHidden() {
        Memory pending = Memory.write(1L, publicDetails(), NOW);
        pending.markPendingReview(NOW);
        Memory hidden = Memory.write(1L, publicDetails(), NOW);
        hidden.hide(NOW);
        Memory active = Memory.write(1L, publicDetails(), NOW);

        pending.restore(NOW);
        hidden.restore(NOW);

        assertThat(pending.getStatus()).isEqualTo(MemoryStatus.ACTIVE);
        assertThat(hidden.getStatus()).isEqualTo(MemoryStatus.ACTIVE);
        assertThatThrownBy(() -> active.restore(NOW))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(MemoryErrorCode.MEMORY_INVALID_STATE));
    }

    @Test
    @DisplayName("[F-33][MM-10] 작성자가 숨긴 기록의 내용과 공개 범위를 고쳐도 상태는 그대로다")
    void reviseKeepsStatus() {
        Memory memory = Memory.write(1L, publicDetails(), NOW);
        memory.hide(NOW);

        memory.revise(details(2, "고친 글"), NOW.plusSeconds(60));
        memory.revise(publicDetails(), NOW.plusSeconds(120));

        assertThat(memory.getStatus()).isEqualTo(MemoryStatus.HIDDEN);
        assertThat(memory.isVisibleToOthers()).isFalse();
    }

    private static MemoryDetails publicDetails() {
        return new MemoryDetails(null, 37.25, 127.25, TODAY, 1, null, MemoryVisibility.PUBLIC);
    }

    private static MemoryDetails details(int nights, String content) {
        return new MemoryDetails(null, 37.25, 127.25, TODAY, nights, content, MemoryVisibility.PRIVATE);
    }
}
