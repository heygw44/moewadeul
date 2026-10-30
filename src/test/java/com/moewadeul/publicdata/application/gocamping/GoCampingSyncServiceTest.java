package com.moewadeul.publicdata.application.gocamping;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import com.moewadeul.publicdata.application.SyncJobRunService;
import com.moewadeul.publicdata.application.SyncJobRunService.SyncJobStart;
import com.moewadeul.publicdata.infra.gocamping.GoCampingClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronizationManager;

class GoCampingSyncServiceTest {

    private final SyncJobRunService syncJobRunService = mock(SyncJobRunService.class);
    private final GoCampingClient goCampingClient = mock(GoCampingClient.class);
    private final GoCampingSyncService service =
            new GoCampingSyncService(syncJobRunService, goCampingClient, null, null, null, null);

    @AfterEach
    void clearTransaction() {
        TransactionSynchronizationManager.setActualTransactionActive(false);
    }

    @Test
    @DisplayName("고캠핑 동기화를 트랜잭션 안에서 실행하면 고캠핑에 묻기 전에 거부한다")
    void rejectsTransaction() {
        TransactionSynchronizationManager.setActualTransactionActive(true);

        assertThatThrownBy(() -> service.run(new SyncJobStart(1L, null)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageStartingWith("트랜잭션 밖에서 불러야 합니다.");
        verifyNoInteractions(goCampingClient, syncJobRunService);
    }
}
