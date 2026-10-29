package com.moewadeul.common.image.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ImageMessagesTest {

    @Test
    void 사진과_이미지_문구에_맞는_조사를_붙인다() {
        assertThat(ImageMessages.PHOTO.tooMany(3)).isEqualTo("사진은 3장까지 붙일 수 있어요.");
        assertThat(ImageMessages.IMAGE.tooMany(3)).isEqualTo("이미지는 3장까지 붙일 수 있어요.");
        assertThat(ImageMessages.PHOTO.duplicated()).isEqualTo("같은 사진을 두 번 붙일 수 없어요.");
        assertThat(ImageMessages.IMAGE.duplicated()).isEqualTo("같은 이미지를 두 번 붙일 수 없어요.");
        assertThat(ImageMessages.PHOTO.unusable()).isEqualTo("붙일 수 없는 사진이 있어요.");
        assertThat(ImageMessages.IMAGE.unusable()).isEqualTo("붙일 수 없는 이미지가 있어요.");
        assertThat(ImageMessages.PHOTO.missingValue()).isEqualTo("사진을 만드는 데 필요한 값이 비었습니다.");
        assertThat(ImageMessages.IMAGE.missingValue()).isEqualTo("이미지를 만드는 데 필요한 값이 비었습니다.");
    }

    @Test
    void 크기_문구는_넘긴_상한에서_만든다() {
        assertThat(ImageMessages.PHOTO.sizeOutOfRange(AttachableImage.MAX_SIZE_BYTES))
                .isEqualTo("사진 크기는 1바이트 이상 5MB 이하여야 해요.");
        assertThat(ImageMessages.IMAGE.sizeOutOfRange(2L * 1024 * 1024)).isEqualTo("이미지 크기는 1바이트 이상 2MB 이하여야 해요.");
        assertThat(ImageMessages.IMAGE.sizeOutOfRange(1500)).isEqualTo("이미지 크기는 1바이트 이상 1500바이트 이하여야 해요.");
    }
}
