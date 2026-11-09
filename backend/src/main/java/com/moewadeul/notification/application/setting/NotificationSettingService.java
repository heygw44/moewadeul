package com.moewadeul.notification.application.setting;

import com.moewadeul.common.error.BusinessException;
import com.moewadeul.common.error.CommonErrorCode;
import com.moewadeul.notification.application.NotificationEventTypes;
import com.moewadeul.notification.domain.setting.NotificationSetting;
import com.moewadeul.notification.domain.setting.NotificationSettingRepository;
import com.moewadeul.notification.infra.setting.NotificationSettingMemberLockMapper;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class NotificationSettingService {

    public static final int TYPE_COUNT = 14;

    private final NotificationSettingRepository notificationSettingRepository;
    private final NotificationSettingMemberLockMapper notificationSettingMemberLockMapper;

    @Transactional(readOnly = true)
    public List<NotificationSettingItem> list(long memberId) {
        return merge(storedByType(memberId));
    }

    @Transactional
    public List<NotificationSettingItem> replace(long memberId, List<NotificationSettingItem> requested) {
        validate(requested);
        notificationSettingMemberLockMapper.lockMember(memberId);
        // 회원 단위 범위 DELETE는 행이 없는 틈에 간격 잠금을 걸어, 이웃한 회원의 INSERT와 교착이 난다. 있는 행만 기본 키로 고치거나 지운다.
        Map<String, NotificationSetting> stored = notificationSettingRepository.findByMemberId(memberId).stream()
                .collect(Collectors.toMap(NotificationSetting::getType, Function.identity()));
        List<NotificationSetting> added = new ArrayList<>();
        for (NotificationSettingItem item : requested) {
            NotificationSetting setting = stored.remove(item.type());
            if (setting == null) {
                added.add(NotificationSetting.of(memberId, item.type(), item.emailEnabled()));
            } else {
                setting.changeEmailEnabled(item.emailEnabled());
            }
        }
        notificationSettingRepository.deleteAll(stored.values());
        notificationSettingRepository.saveAll(added);
        return merge(requested.stream().collect(Collectors.toMap(NotificationSettingItem::type, Function.identity())));
    }

    private Map<String, NotificationSettingItem> storedByType(long memberId) {
        return notificationSettingRepository.findByMemberId(memberId).stream()
                .collect(Collectors.toMap(
                        NotificationSetting::getType,
                        setting -> new NotificationSettingItem(setting.getType(), setting.isEmailEnabled())));
    }

    private static List<NotificationSettingItem> merge(Map<String, NotificationSettingItem> storedByType) {
        return NotificationEventTypes.EMAIL_CONFIGURABLE.stream()
                .map(type -> storedByType.getOrDefault(
                        type, new NotificationSettingItem(type, NotificationEventTypes.DEFAULT_EMAIL_ENABLED)))
                .toList();
    }

    private static void validate(List<NotificationSettingItem> requested) {
        Set<String> seen = new HashSet<>();
        for (NotificationSettingItem item : requested) {
            if (!NotificationEventTypes.EMAIL_CONFIGURABLE.contains(item.type())) {
                throw new BusinessException(CommonErrorCode.INVALID_INPUT, "알 수 없는 알림 종류예요.");
            }
            if (!seen.add(item.type())) {
                throw new BusinessException(CommonErrorCode.INVALID_INPUT, "같은 알림 종류를 두 번 보낼 수 없어요.");
            }
        }
    }
}
