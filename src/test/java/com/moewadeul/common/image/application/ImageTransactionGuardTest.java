package com.moewadeul.common.image.application;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.moewadeul.common.image.domain.AttachableImage;
import com.moewadeul.common.image.domain.AttachableImage.Thumbnail;
import com.moewadeul.common.image.domain.ImageKind;
import com.moewadeul.common.image.domain.ImageMessages;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronizationManager;

class ImageTransactionGuardTest {

    private static final ImageKind KIND = new ImageKind("test", ImageMessages.IMAGE, 3);

    @AfterEach
    void clearTransaction() {
        TransactionSynchronizationManager.setActualTransactionActive(false);
    }

    @Test
    @DisplayName("업로드 URL 발급을 트랜잭션 밖에서 부르면 회원 행을 잠그기 전에 거부한다")
    void issueUploadRequiresTransaction() {
        ImageUploadIssuer<AttachableImage> issuer = new ImageUploadIssuer<>(KIND, null, null, null, null) {
            @Override
            protected AttachableImage newImage(
                    long memberId,
                    String objectKey,
                    String contentType,
                    long sizeBytes,
                    Instant now,
                    Thumbnail thumbnail) {
                return null;
            }
        };

        assertThatThrownBy(() -> issuer.issue(1L, new ImageUploadCommand("image/png", 10, null)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageStartingWith("트랜잭션 안에서 불러야 합니다.");
    }

    @Test
    @DisplayName("이미지 목록 교체를 트랜잭션 밖에서 부르면 행을 잠그기 전에 거부한다")
    void replaceRequiresTransaction() {
        ImageAttachments<AttachableImage> attachments = new ImageAttachments<>(KIND, null, null) {};

        assertThatThrownBy(() -> attachments.replace(1L, 2L, List.of(3L)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageStartingWith("트랜잭션 안에서 불러야 합니다.");
    }

    @Test
    @DisplayName("첨부 사전 확인을 트랜잭션 안에서 부르면 저장소에 묻기 전에 거부한다")
    void precheckRejectsTransaction() {
        ImageAttachments<AttachableImage> attachments = new ImageAttachments<>(KIND, null, null) {};
        TransactionSynchronizationManager.setActualTransactionActive(true);

        assertThatThrownBy(() -> attachments.precheck(1L, null, List.of(3L)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageStartingWith("트랜잭션 밖에서 불러야 합니다.");
    }

    @Test
    @DisplayName("정리 작업을 트랜잭션 안에서 부르면 행을 지우기 전에 거부한다")
    void cleanupRejectsTransaction() {
        ImageCleanup cleanup = new ImageCleanup(KIND, null, null, null) {};
        TransactionSynchronizationManager.setActualTransactionActive(true);

        assertThatThrownBy(cleanup::deleteExpiredRowsThenObjects)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageStartingWith("트랜잭션 밖에서 불러야 합니다.");
    }
}
