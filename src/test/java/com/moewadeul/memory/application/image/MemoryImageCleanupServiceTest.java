package com.moewadeul.memory.application.image;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import com.moewadeul.common.storage.ImageStorage;
import com.moewadeul.memory.infra.image.MemoryImageMapper;
import java.time.Clock;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronizationManager;

class MemoryImageCleanupServiceTest {

    private final MemoryImageMapper memoryImageMapper = mock(MemoryImageMapper.class);
    private final ImageStorage imageStorage = mock(ImageStorage.class);
    private final MemoryImageCleanupService service =
            new MemoryImageCleanupService(memoryImageMapper, imageStorage, Clock.systemUTC());

    @AfterEach
    void clearTransaction() {
        TransactionSynchronizationManager.setActualTransactionActive(false);
    }

    @Test
    @DisplayName("회원의 추억 이미지 정리를 트랜잭션 안에서 부르면 저장소 삭제 전에 거부한다")
    void rejectsTransaction() {
        TransactionSynchronizationManager.setActualTransactionActive(true);

        assertThatThrownBy(() -> service.deleteObjectsThenRowsOf(1L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageStartingWith("트랜잭션 밖에서 불러야 합니다.");
        verifyNoInteractions(memoryImageMapper, imageStorage);
    }
}
