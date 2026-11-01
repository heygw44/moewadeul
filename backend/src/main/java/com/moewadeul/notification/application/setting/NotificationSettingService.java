package com.moewadeul.notification.application.setting;

import com.moewadeul.common.error.BusinessException;
import com.moewadeul.common.error.CommonErrorCode;
import com.moewadeul.notification.application.NotificationEventTypes;
import com.moewadeul.notification.domain.setting.NotificationSetting;
import com.moewadeul.notification.domain.setting.NotificationSettingRepository;
import com.moewadeul.notification.infra.setting.NotificationSettingMemberLockMapper;
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
        notificationSettingRepository.deleteByMemberId(memberId);
        List<NotificationSetting> settings = requested.stream()
                .map(item -> NotificationSetting.of(memberId, item.type(), item.emailEnabled()))
                .toList();
        notificationSettingRepository.saveAll(settings);
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
