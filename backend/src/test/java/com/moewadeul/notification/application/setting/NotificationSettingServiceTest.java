package com.moewadeul.notification.application.setting;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.moewadeul.common.error.BusinessException;
import com.moewadeul.common.error.CommonErrorCode;
import com.moewadeul.common.event.BasecampEvents;
import com.moewadeul.notification.application.NotificationEventTypes;
import com.moewadeul.notification.domain.setting.NotificationSetting;
import com.moewadeul.notification.domain.setting.NotificationSettingRepository;
import com.moewadeul.notification.infra.setting.NotificationSettingMemberLockMapper;
import java.util.Collection;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class NotificationSettingServiceTest {

    private static final long MEMBER_ID = 7L;

    private final NotificationSettingRepository repository = mock(NotificationSettingRepository.class);
    private final NotificationSettingService service =
            new NotificationSettingService(repository, mock(NotificationSettingMemberLockMapper.class));

    @Test
    @DisplayName("[F-20] 요청 길이의 상한은 설정할 수 있는 알림 종류의 수와 같다")
    void typeCountMatchesConfigurableTypes() {
        assertThat(NotificationSettingService.TYPE_COUNT).isEqualTo(NotificationEventTypes.EMAIL_CONFIGURABLE.size());
    }

    @Test
    @DisplayName("[F-20] 저장한 설정이 없으면 모든 종류를 정해진 순서로 기본값(이메일 받기)으로 돌려준다")
    void listReturnsAllTypesWithDefault() {
        when(repository.findByMemberId(MEMBER_ID)).thenReturn(List.of());

        List<NotificationSettingItem> items = service.list(MEMBER_ID);

        assertThat(items)
                .extracting(NotificationSettingItem::type)
                .containsExactlyElementsOf(NotificationEventTypes.EMAIL_CONFIGURABLE);
        assertThat(items).allMatch(NotificationSettingItem::emailEnabled);
    }

    @Test
    @DisplayName("[F-20] 저장한 설정은 해당 종류에 덮어쓰고 나머지는 기본값이다")
    void listMergesStoredSettings() {
        when(repository.findByMemberId(MEMBER_ID))
                .thenReturn(List.of(NotificationSetting.of(MEMBER_ID, BasecampEvents.KICKED, false)));

        List<NotificationSettingItem> items = service.list(MEMBER_ID);

        assertThat(items)
                .filteredOn(item -> !item.emailEnabled())
                .extracting(NotificationSettingItem::type)
                .containsExactly(BasecampEvents.KICKED);
        assertThat(items).hasSize(NotificationEventTypes.EMAIL_CONFIGURABLE.size());
    }

    @Test
    @DisplayName("[F-20] 설정을 바꾸면 요청에 있는 저장된 설정은 고치고, 없던 종류는 새로 저장하고, 요청에서 빠진 저장된 설정은 지운다")
    void replaceUpdatesAddsAndDeletesStoredSettings() {
        NotificationSetting kept = NotificationSetting.of(MEMBER_ID, BasecampEvents.KICKED, false);
        NotificationSetting omitted = NotificationSetting.of(MEMBER_ID, BasecampEvents.APPROVED, false);
        when(repository.findByMemberId(MEMBER_ID)).thenReturn(List.of(kept, omitted));
        List<NotificationSettingItem> requested = List.of(
                new NotificationSettingItem(BasecampEvents.APPLIED, false),
                new NotificationSettingItem(BasecampEvents.KICKED, true));

        List<NotificationSettingItem> result = service.replace(MEMBER_ID, requested);

        assertThat(kept.isEmailEnabled()).isTrue();
        verify(repository)
                .deleteAll(argThat(
                        settings -> List.copyOf((Collection<?>) settings).equals(List.of(omitted))));
        verify(repository)
                .saveAll(argThat((List<NotificationSetting> added) -> added.size() == 1
                        && added.get(0).getType().equals(BasecampEvents.APPLIED)
                        && !added.get(0).isEmailEnabled()));
        assertThat(result)
                .filteredOn(item -> !item.emailEnabled())
                .extracting(NotificationSettingItem::type)
                .containsExactly(BasecampEvents.APPLIED);
    }

    @Test
    @DisplayName("[F-20] 모르는 알림 종류면 INVALID_INPUT이고 아무것도 지우거나 저장하지 않는다")
    void replaceRejectsUnknownType() {
        List<NotificationSettingItem> requested = List.of(new NotificationSettingItem("NO_SUCH_TYPE", false));

        assertThatThrownBy(() -> service.replace(MEMBER_ID, requested))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(CommonErrorCode.INVALID_INPUT));
        verify(repository, never()).saveAll(any());
    }

    @Test
    @DisplayName("[F-20] 같은 알림 종류가 두 번 있으면 INVALID_INPUT이고 아무것도 지우거나 저장하지 않는다")
    void replaceRejectsDuplicateType() {
        List<NotificationSettingItem> requested = List.of(
                new NotificationSettingItem(BasecampEvents.APPLIED, false),
                new NotificationSettingItem(BasecampEvents.APPLIED, true));

        assertThatThrownBy(() -> service.replace(MEMBER_ID, requested))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(CommonErrorCode.INVALID_INPUT));
        verify(repository, never()).saveAll(any());
    }
}
