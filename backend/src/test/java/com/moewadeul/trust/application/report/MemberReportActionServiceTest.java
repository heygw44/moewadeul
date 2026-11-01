package com.moewadeul.trust.application.report;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.moewadeul.common.error.BusinessException;
import com.moewadeul.common.error.CommonErrorCode;
import com.moewadeul.member.application.MemberSessionCleanup;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class MemberReportActionServiceTest {

    @ParameterizedTest
    @ValueSource(strings = {"", "temporary_7d", "TEMPORARY_72H", "UNKNOWN"})
    @DisplayName("회원 신고 처리는 관리자가 내릴 수 없는 제재 종류를 INVALID_INPUT으로 거절한다")
    void rejectsUnassignableSanction(String sanctionType) {
        MemberReportActionService service =
                new MemberReportActionService(mock(MemberReportActionApplier.class), mock(MemberSessionCleanup.class));

        assertThatThrownBy(() -> service.act(new MemberReportActionCommand(1L, 2L, sanctionType, null, false, null)))
                .isInstanceOfSatisfying(BusinessException.class, e -> {
                    assertThat(e.getErrorCode()).isEqualTo(CommonErrorCode.INVALID_INPUT);
                    assertThat(e.getMessage()).isEqualTo("관리자가 내릴 수 없는 제재 종류예요.");
                });
    }

    @Test
    @DisplayName("회원 신고 처리는 제재 종류가 null이면 제재 없이 처리한다")
    void allowsNullSanction() {
        MemberReportActionApplier applier = mock(MemberReportActionApplier.class);
        MemberReportActionCommand command = new MemberReportActionCommand(1L, 2L, null, null, false, null);
        when(applier.apply(command, null)).thenReturn(new MemberReportActionResult(1L, "DISMISSED", null, 3L, false));
        MemberReportActionService service = new MemberReportActionService(applier, mock(MemberSessionCleanup.class));

        service.act(command);

        verify(applier).apply(command, null);
    }
}
