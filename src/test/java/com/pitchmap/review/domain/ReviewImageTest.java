package com.pitchmap.review.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.pitchmap.common.testsupport.MutableClock;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ReviewImageTest {

    private static final Instant NOW = MutableClock.DEFAULT_INSTANT;

    @ParameterizedTest
    @ValueSource(strings = {"image/jpeg", "image/png", "image/webp"})
    @DisplayName("[F-24][RI-01] JPEG·PNG·WebP는 후기에 붙지 않은 이미지로 만든다")
    void issuesAllowedTypes(String contentType) {
        ReviewImage image = ReviewImage.issue(1L, "review/1/a", contentType, 100, NOW);

        assertThat(image.isAttached()).isFalse();
        assertThat(image.isUploadedBy(1L)).isTrue();
        assertThat(image.isUploadedBy(2L)).isFalse();
    }

    @Test
    @DisplayName("[F-24][RI-01] GIF와 0바이트, 5MB를 1바이트 넘는 이미지는 만들지 않는다")
    void rejectsDisallowedTypeAndSize() {
        assertThatThrownBy(() -> ReviewImage.issue(1L, "k", "image/gif", 100, NOW))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ReviewImage.issue(1L, "k", "image/png", 0, NOW))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ReviewImage.issue(1L, "k", "image/png", ReviewImage.MAX_SIZE_BYTES + 1, NOW))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(ReviewImage.isAllowedSize(ReviewImage.MAX_SIZE_BYTES)).isTrue();
    }

    @Test
    @DisplayName("[F-24][RI-02] 후기에 붙이면 후기 안 순서를 기록하고, 3번째(순서 2)까지만 붙인다")
    void attachesUpToThirdOrder() {
        ReviewImage image = ReviewImage.issue(1L, "k", "image/png", 100, NOW);

        image.attachTo(7L, 2);

        assertThat(image.isAttachedTo(7L)).isTrue();
        assertThat(image.getDisplayOrder()).isEqualTo(2);
        assertThatThrownBy(() -> image.attachTo(7L, ReviewImage.MAX_PER_REVIEW))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("[F-24][RI-03] 후기에서 떼면 붙지 않은 상태로 돌아간다")
    void detachReturnsToUnattached() {
        ReviewImage image = ReviewImage.issue(1L, "k", "image/png", 100, NOW);
        image.attachTo(7L, 0);

        image.detach();

        assertThat(image.isAttached()).isFalse();
        assertThat(image.getDisplayOrder()).isNull();
    }
}
