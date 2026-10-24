package com.moewadeul.program.application.enrollment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.moewadeul.common.testsupport.MutableClock;
import com.moewadeul.program.infra.enrollment.ExpiryTarget;
import com.moewadeul.program.infra.enrollment.ProgramPaymentExpiryMapper;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.CannotAcquireLockException;

class ProgramPaymentExpiryServiceTest {

    private final ProgramPaymentExpiryMapper mapper = mock(ProgramPaymentExpiryMapper.class);
    private final ProgramPaymentExpiryApplier applier = mock(ProgramPaymentExpiryApplier.class);
    private final ProgramPaymentExpiryService service =
            new ProgramPaymentExpiryService(mapper, applier, MutableClock.atDefaultInstant());

    @Test
    @DisplayName("[NFR-09][PG-05] 신청 하나가 예외로 실패해도 나머지는 계속 처리하고, 예외로 실패한 수만 failed에 센다")
    void countsOnlyFailuresThatThrew() {
        // given: 1번은 잠금 대기 시간 초과, 2번은 결제가 먼저 확정해 바뀐 것이 없음, 3번은 정상 만료
        ExpiryTarget lockTimeout = new ExpiryTarget(1L, 10L, 100L);
        ExpiryTarget alreadyPaid = new ExpiryTarget(2L, 20L, 100L);
        ExpiryTarget due = new ExpiryTarget(3L, 30L, 100L);
        when(mapper.selectDueTargets(any(), eq(0L), anyInt())).thenReturn(List.of(lockTimeout, alreadyPaid, due));
        when(applier.expire(eq(lockTimeout), any())).thenThrow(new CannotAcquireLockException("Lock wait timeout"));
        when(applier.expire(eq(alreadyPaid), any())).thenReturn(false);
        when(applier.expire(eq(due), any())).thenReturn(true);

        // when
        ProgramPaymentExpiryService.Result result = service.run();

        // then
        assertThat(result).isEqualTo(new ProgramPaymentExpiryService.Result(1, 1));
    }
}
