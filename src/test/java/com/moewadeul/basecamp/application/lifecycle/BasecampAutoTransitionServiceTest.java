package com.moewadeul.basecamp.application.lifecycle;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.moewadeul.basecamp.infra.lifecycle.BasecampAutoTransitionMapper;
import com.moewadeul.common.testsupport.MutableClock;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.CannotAcquireLockException;

class BasecampAutoTransitionServiceTest {

    private final BasecampAutoTransitionMapper mapper = mock(BasecampAutoTransitionMapper.class);
    private final BasecampAutoTransitionApplier applier = mock(BasecampAutoTransitionApplier.class);
    private final BasecampAutoTransitionService service =
            new BasecampAutoTransitionService(mapper, applier, MutableClock.atDefaultInstant());

    @Test
    @DisplayName("[NFR-09][BC-10] 베이스캠프 하나가 예외로 실패해도 나머지는 계속 처리하고, 예외로 실패한 수만 failed에 센다")
    void countsOnlyFailuresThatThrew() {
        // given
        when(mapper.selectDueForDepartureIds(any(), eq(0L), anyInt())).thenReturn(List.of(1L, 2L, 3L));
        when(applier.processDayBeforeDeparture(eq(1L), any()))
                .thenThrow(new CannotAcquireLockException("Lock wait timeout exceeded"));
        when(applier.processDayBeforeDeparture(eq(2L), any())).thenReturn(false);
        when(applier.processDayBeforeDeparture(eq(3L), any())).thenReturn(true);

        // when
        BasecampAutoTransitionService.Result result = service.run();

        // then
        assertThat(result).isEqualTo(new BasecampAutoTransitionService.Result(0, 1, 1));
    }

    @Test
    @DisplayName("[NFR-09][BC-10] 완료 처리가 예외로 실패해도 출발 전날 처리는 이어서 하고, 두 단계의 실패 수를 합친다")
    void sumsFailuresOfBothSteps() {
        // given
        when(mapper.selectDueForCompletionIds(any(), eq(0L), anyInt())).thenReturn(List.of(1L));
        when(mapper.selectDueForDepartureIds(any(), eq(0L), anyInt())).thenReturn(List.of(2L, 3L));
        when(applier.complete(eq(1L), any(), any())).thenThrow(new IllegalStateException("DB 오류"));
        when(applier.processDayBeforeDeparture(eq(2L), any())).thenThrow(new IllegalStateException("DB 오류"));
        when(applier.processDayBeforeDeparture(eq(3L), any())).thenReturn(true);

        // when
        BasecampAutoTransitionService.Result result = service.run();

        // then
        assertThat(result).isEqualTo(new BasecampAutoTransitionService.Result(0, 1, 2));
    }
}
