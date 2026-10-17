package com.moewadeul.spot.application.bakji;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.moewadeul.common.error.BusinessException;
import com.moewadeul.common.error.CommonErrorCode;
import com.moewadeul.common.web.PatchField;
import com.moewadeul.spot.application.SpotParkWarning;
import com.moewadeul.spot.domain.SpotErrorCode;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

class BakjiEditServiceTest {

    private static final long MEMBER_ID = 7L;
    private static final long SPOT_ID = 205L;
    private static final BakjiUpdateCommand RENAME = new BakjiUpdateCommand(
            PatchField.of("새 이름"),
            PatchField.absent(),
            PatchField.absent(),
            PatchField.absent(),
            PatchField.absent(),
            PatchField.absent(),
            PatchField.absent(),
            PatchField.absent());
    private static final BakjiSubmission SUBMISSION =
            new BakjiSubmission(SPOT_ID, SpotParkWarning.notWarned(), "안내", null, List.of());

    private final BakjiCommandService commandService = mock(BakjiCommandService.class);
    private final BakjiEditService editService = new BakjiEditService(commandService);

    @Test
    @DisplayName("[F-07][BC-04] 박지 수정이 낙관적 잠금 충돌로 한 번 실패하면 서비스가 다시 적용해 결과를 돌려준다")
    void updateRetriesOnceAfterConflict() {
        // given
        when(commandService.update(MEMBER_ID, SPOT_ID, RENAME))
                .thenThrow(conflict())
                .thenReturn(SUBMISSION);

        // when
        BakjiSubmission result = editService.update(MEMBER_ID, SPOT_ID, RENAME);

        // then
        assertThat(result).isEqualTo(SUBMISSION);
        verify(commandService, times(2)).update(MEMBER_ID, SPOT_ID, RENAME);
    }

    @Test
    @DisplayName("[F-07][BC-04] 다시 적용해도 충돌하면 BAKJI_CONCURRENTLY_MODIFIED로 거부하고 세 번째는 시도하지 않는다")
    void updateGivesUpAfterSecondConflict() {
        // given
        when(commandService.update(MEMBER_ID, SPOT_ID, RENAME)).thenThrow(conflict(), conflict());

        // when, then
        assertThatThrownBy(() -> editService.update(MEMBER_ID, SPOT_ID, RENAME))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(SpotErrorCode.BAKJI_CONCURRENTLY_MODIFIED));
        verify(commandService, times(2)).update(MEMBER_ID, SPOT_ID, RENAME);
    }

    @Test
    @DisplayName("[F-07] 다시 읽은 박지가 그 사이 숨겨졌으면 다시 적용할 때의 거부를 그대로 돌려준다")
    void updatePassesThroughRejectionOnRetry() {
        // given
        BusinessException notFound = new BusinessException(CommonErrorCode.NOT_FOUND);
        when(commandService.update(MEMBER_ID, SPOT_ID, RENAME))
                .thenThrow(conflict())
                .thenThrow(notFound);

        // when, then
        assertThatThrownBy(() -> editService.update(MEMBER_ID, SPOT_ID, RENAME)).isSameAs(notFound);
    }

    @Test
    @DisplayName("[F-07] 박지 삭제도 충돌로 한 번 실패하면 다시 적용하고, 두 번째도 충돌하면 BAKJI_CONCURRENTLY_MODIFIED다")
    void deleteRetriesOnceThenGivesUp() {
        // given
        doThrow(conflict()).doNothing().when(commandService).delete(MEMBER_ID, SPOT_ID);

        // when
        editService.delete(MEMBER_ID, SPOT_ID);

        // then
        verify(commandService, times(2)).delete(MEMBER_ID, SPOT_ID);

        // given
        BakjiCommandService alwaysConflicting = mock(BakjiCommandService.class);
        doThrow(conflict()).when(alwaysConflicting).delete(any(Long.class), any(Long.class));

        // when, then
        assertThatThrownBy(() -> new BakjiEditService(alwaysConflicting).delete(MEMBER_ID, SPOT_ID))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(SpotErrorCode.BAKJI_CONCURRENTLY_MODIFIED));
    }

    private static ObjectOptimisticLockingFailureException conflict() {
        return new ObjectOptimisticLockingFailureException("Spot", SPOT_ID);
    }
}
