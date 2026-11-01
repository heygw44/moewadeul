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

    @Test
    @DisplayName("[F-33][MM-05] 썸네일 크기는 1바이트부터 256KB(262144바이트)까지 허용한다")
    void thumbnailSizeBoundaries() {
        assertThat(MemoryImage.isAllowedThumbnailSize(0)).isFalse();
        assertThat(MemoryImage.isAllowedThumbnailSize(1)).isTrue();
        assertThat(MemoryImage.isAllowedThumbnailSize(262_144L)).isTrue();
        assertThat(MemoryImage.isAllowedThumbnailSize(262_145L)).isFalse();
        assertThatThrownBy(() -> withThumbnail("memory/7/key_thumb.jpg", 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> withThumbnail("memory/7/key_thumb.jpg", 262_145L))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("[F-33][MM-05] 썸네일과 함께 만든 사진은 썸네일 키와 크기를 갖고, 썸네일 없이 만든 사진은 둘 다 없다")
    void issuesWithOrWithoutThumbnail() {
        // when
        MemoryImage smallest = withThumbnail("memory/7/key_thumb.jpg", 1);
        MemoryImage largest = withThumbnail("memory/7/key_thumb.jpg", 262_144L);
        MemoryImage plain = MemoryImage.issue(7L, "memory/7/key.png", "image/png", 1000L, NOW);

        // then
        assertThat(smallest.hasThumbnail()).isTrue();
        assertThat(smallest.getThumbnailObjectKey()).isEqualTo("memory/7/key_thumb.jpg");
        assertThat(smallest.getThumbnailSizeBytes()).isEqualTo(1);
        assertThat(largest.getThumbnailSizeBytes()).isEqualTo(262_144);
        assertThat(plain.hasThumbnail()).isFalse();
        assertThat(plain.getThumbnailObjectKey()).isNull();
        assertThat(plain.getThumbnailSizeBytes()).isNull();
    }

    @Test
    @DisplayName("[F-33][MM-05] 썸네일 키가 비었으면 만들지 않는다")
    void rejectsBlankThumbnailKey() {
        assertThatThrownBy(() -> withThumbnail(" ", 100)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> withThumbnail(null, 100)).isInstanceOf(IllegalArgumentException.class);
    }

    private static MemoryImage withThumbnail(String thumbnailKey, long thumbnailSizeBytes) {
        return MemoryImage.issue(
                7L,
                "memory/7/key.png",
                "image/png",
                1000L,
                NOW,
                new MemoryImage.Thumbnail(thumbnailKey, thumbnailSizeBytes));
    }
}
