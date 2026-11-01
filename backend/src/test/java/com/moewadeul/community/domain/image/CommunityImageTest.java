package com.moewadeul.community.domain.image;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class CommunityImageTest {

    private static final Instant NOW = Instant.parse("2026-10-10T00:00:00Z");

    private static CommunityImage issued() {
        return CommunityImage.issue(7L, "community/7/key.jpg", "image/jpeg", 1000L, NOW);
    }

    @Test
    @DisplayName("[F-29][CM-06] 이미지를 발급하면 글에 붙지 않은 상태이고 올린 회원만 올린 이미지로 본다")
    void issuedImageIsUnattached() {
        // when
        CommunityImage image = issued();

        // then
        assertThat(image.isAttached()).isFalse();
        assertThat(image.getDisplayOrder()).isNull();
        assertThat(image.isUploadedBy(7L)).isTrue();
        assertThat(image.isUploadedBy(8L)).isFalse();
    }

    @ParameterizedTest
    @CsvSource({"image/jpeg,jpg", "image/png,png", "image/webp,webp"})
    @DisplayName("[F-29][CM-06] JPEG, PNG, WebP를 허용하고 형식마다 확장자를 정한다")
    void allowedContentTypesHaveExtensions(String contentType, String extension) {
        assertThat(CommunityImage.isAllowedContentType(contentType)).isTrue();
        assertThat(CommunityImage.extensionOf(contentType)).contains(extension);
    }

    @Test
    @DisplayName("[F-29][CM-06] GIF와 null은 허용하지 않는다")
    void otherContentTypesAreRejected() {
        assertThat(CommunityImage.isAllowedContentType("image/gif")).isFalse();
        assertThat(CommunityImage.isAllowedContentType(null)).isFalse();
        assertThat(CommunityImage.extensionOf("image/gif")).isEmpty();
        assertThatThrownBy(() -> CommunityImage.issue(7L, "k", "image/gif", 1000L, NOW))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("[F-29][CM-06] 크기는 1바이트부터 5MB(5242880바이트)까지 허용한다")
    void sizeBoundaries() {
        assertThat(CommunityImage.isAllowedSize(1)).isTrue();
        assertThat(CommunityImage.isAllowedSize(5_242_880L)).isTrue();
        assertThat(CommunityImage.isAllowedSize(0)).isFalse();
        assertThat(CommunityImage.isAllowedSize(5_242_881L)).isFalse();
        assertThatThrownBy(() -> CommunityImage.issue(7L, "k", "image/png", 5_242_881L, NOW))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("[F-29][CM-06] 글에 붙이면 글과 순서를 기록하고, 떼면 붙지 않은 상태로 돌아간다")
    void attachAndDetach() {
        // given
        CommunityImage image = issued();

        // when
        image.attachTo(9L, 2);

        // then
        assertThat(image.isAttached()).isTrue();
        assertThat(image.isAttachedTo(9L)).isTrue();
        assertThat(image.isAttachedTo(10L)).isFalse();
        assertThat(image.getDisplayOrder()).isEqualTo(2);

        // when
        image.detach();

        // then
        assertThat(image.isAttached()).isFalse();
        assertThat(image.getParentId()).isNull();
        assertThat(image.getDisplayOrder()).isNull();
    }

    @Test
    @DisplayName("[F-29][CM-06] 글 안 순서는 0부터 4까지만 허용한다")
    void orderRange() {
        CommunityImage image = issued();
        assertThatThrownBy(() -> image.attachTo(9L, -1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> image.attachTo(9L, 5)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("[F-29][CM-06] 썸네일 크기는 1바이트부터 256KB(262144바이트)까지 허용한다")
    void thumbnailSizeBoundaries() {
        assertThat(CommunityImage.isAllowedThumbnailSize(0)).isFalse();
        assertThat(CommunityImage.isAllowedThumbnailSize(1)).isTrue();
        assertThat(CommunityImage.isAllowedThumbnailSize(262_144L)).isTrue();
        assertThat(CommunityImage.isAllowedThumbnailSize(262_145L)).isFalse();
        assertThatThrownBy(() -> withThumbnail("community/7/key_thumb.jpg", 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> withThumbnail("community/7/key_thumb.jpg", 262_145L))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("[F-29][CM-06] 썸네일과 함께 만든 이미지는 썸네일 키와 크기를 갖고, 썸네일 없이 만든 이미지는 둘 다 없다")
    void issuesWithOrWithoutThumbnail() {
        // when
        CommunityImage smallest = withThumbnail("community/7/key_thumb.jpg", 1);
        CommunityImage largest = withThumbnail("community/7/key_thumb.jpg", 262_144L);
        CommunityImage plain = CommunityImage.issue(7L, "community/7/key.png", "image/png", 1000L, NOW);

        // then
        assertThat(smallest.hasThumbnail()).isTrue();
        assertThat(smallest.getThumbnailObjectKey()).isEqualTo("community/7/key_thumb.jpg");
        assertThat(smallest.getThumbnailSizeBytes()).isEqualTo(1);
        assertThat(largest.getThumbnailSizeBytes()).isEqualTo(262_144);
        assertThat(plain.hasThumbnail()).isFalse();
        assertThat(plain.getThumbnailObjectKey()).isNull();
        assertThat(plain.getThumbnailSizeBytes()).isNull();
    }

    @Test
    @DisplayName("[F-29][CM-06] 썸네일 키가 비었으면 만들지 않는다")
    void rejectsBlankThumbnailKey() {
        assertThatThrownBy(() -> withThumbnail(" ", 100)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> withThumbnail(null, 100)).isInstanceOf(IllegalArgumentException.class);
    }

    private static CommunityImage withThumbnail(String thumbnailKey, long thumbnailSizeBytes) {
        return CommunityImage.issue(
                7L,
                "community/7/key.png",
                "image/png",
                1000L,
                NOW,
                new CommunityImage.Thumbnail(thumbnailKey, thumbnailSizeBytes));
    }
}
