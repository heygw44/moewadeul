package com.moewadeul.publicdata.application.gocamping;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.moewadeul.publicdata.application.SyncJobRunService;
import com.moewadeul.publicdata.application.SyncJobRunService.SyncJobStart;
import com.moewadeul.publicdata.domain.ResumePoint;
import com.moewadeul.publicdata.infra.gocamping.GoCampingClient;
import com.moewadeul.publicdata.infra.gocamping.GoCampingPage;
import com.moewadeul.publicdata.infra.gocamping.GoCampingProperties;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.transaction.support.TransactionSynchronizationManager;

class GoCampingSyncServiceTest {

    private static final int PAGE_SIZE = 10;
    private static final GoCampingPage EMPTY_PAGE = new GoCampingPage(0, 1, PAGE_SIZE, List.of());

    private final SyncJobRunService syncJobRunService = mock(SyncJobRunService.class);
    private final GoCampingClient goCampingClient = mock(GoCampingClient.class);
    private final GoCampingSyncService service = new GoCampingSyncService(
            syncJobRunService,
            goCampingClient,
            null,
            new GoCampingProperties(
                    "https://example.test", "key", Duration.ofSeconds(1), Duration.ofSeconds(1), PAGE_SIZE),
            null,
            null);

    @AfterEach
    void clearTransaction() {
        TransactionSynchronizationManager.setActualTransactionActive(false);
    }

    @Test
    @DisplayName("고캠핑 동기화를 트랜잭션 안에서 실행하면 고캠핑에 묻기 전에 거부한다")
    void rejectsTransaction() {
        TransactionSynchronizationManager.setActualTransactionActive(true);

        assertThatThrownBy(() -> service.run(new SyncJobStart(1L, Optional.empty())))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageStartingWith("트랜잭션 밖에서 불러야 합니다.");
        verifyNoInteractions(goCampingClient, syncJobRunService);
    }

    @Test
    @DisplayName("[F-06] 이어받은 직후 또 실패한 페이지는 건너뛰고, 건너뛴 페이지를 이 실행의 진행 위치에 올린 뒤 그다음 페이지부터 받는다")
    void skipsPageThatFailedTwiceAndRecordsItAsProgress() {
        when(goCampingClient.fetchPage(anyInt(), anyInt())).thenReturn(EMPTY_PAGE);

        service.run(new SyncJobStart(7L, Optional.of(new ResumePoint("2", true))));

        InOrder inOrder = inOrder(syncJobRunService, goCampingClient);
        inOrder.verify(syncJobRunService).recordProgress(7L, "3", 0, 0);
        inOrder.verify(goCampingClient).fetchPage(4, PAGE_SIZE);
        inOrder.verify(syncJobRunService).complete(7L);
        verify(goCampingClient, never()).fetchPage(3, PAGE_SIZE);
    }

    @Test
    @DisplayName("[F-06] 진행을 기록한 채 실패한 실행을 이어받으면 페이지를 건너뛰지 않고 위치 다음 페이지부터 받는다")
    void resumesFromNextPageWithoutSkipping() {
        when(goCampingClient.fetchPage(anyInt(), anyInt())).thenReturn(EMPTY_PAGE);

        service.run(new SyncJobStart(7L, Optional.of(new ResumePoint("2", false))));

        verify(goCampingClient).fetchPage(3, PAGE_SIZE);
        verify(syncJobRunService, never()).recordProgress(anyLong(), any(), anyInt(), anyInt());
    }
}
