package com.moewadeul.memory.domain.image;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.moewadeul.common.testsupport.MutableClock;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class MemoryImageTest {

    private static final Instant NOW = MutableClock.DEFAULT_INSTANT;

    @ParameterizedTest
    @ValueSource(strings = {"image/jpeg", "image/png", "image/webp"})
    @DisplayName("[F-33][MM-05] JPEG·PNG·WebP는 기록에 붙지 않은 사진으로 만든다")
    void issuesAllowedTypes(String contentType) {
        MemoryImage image = MemoryImage.issue(1L, "memory/1/a", contentType, 100, NOW);

        assertThat(image.isAttached()).isFalse();
        assertThat(image.isUploadedBy(1L)).isTrue();
        assertThat(image.isUploadedBy(2L)).isFalse();
    }

    @Test
    @DisplayName("[F-33][MM-05] GIF와 0바이트, 5MB를 1바이트 넘는 사진은 만들지 않는다")
    void rejectsDisallowedTypeAndSize() {
        assertThatThrownBy(() -> MemoryImage.issue(1L, "k", "image/gif", 100, NOW))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> MemoryImage.issue(1L, "k", "image/png", 0, NOW))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> MemoryImage.issue(1L, "k", "image/png", MemoryImage.MAX_SIZE_BYTES + 1, NOW))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(MemoryImage.isAllowedSize(MemoryImage.MAX_SIZE_BYTES)).isTrue();
    }

    @Test
    @DisplayName("[F-33][MM-05] 기록에 붙이면 기록 안 순서를 기록하고, 10번째(순서 9)까지만 붙인다")
    void attachesUpToTenthOrder() {
        MemoryImage image = MemoryImage.issue(1L, "k", "image/png", 100, NOW);

        image.attachTo(7L, 9);

        assertThat(image.isAttachedTo(7L)).isTrue();
        assertThat(image.getDisplayOrder()).isEqualTo(9);
        assertThatThrownBy(() -> image.attachTo(7L, MemoryImage.MAX_PER_MEMORY))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("[F-33][MM-05] 기록에서 떼면 붙지 않은 상태로 돌아간다")
    void detachReturnsToUnattached() {
        MemoryImage image = MemoryImage.issue(1L, "k", "image/png", 100, NOW);
        image.attachTo(7L, 0);

        image.detach();

        assertThat(image.isAttached()).isFalse();
        assertThat(image.getDisplayOrder()).isNull();
    }
}
