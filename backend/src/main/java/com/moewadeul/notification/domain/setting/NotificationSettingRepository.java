package com.moewadeul.notification.domain.setting;

import java.util.List;

public interface NotificationSettingRepository {

    List<NotificationSetting> findByMemberId(long memberId);

    void deleteAll(Iterable<? extends NotificationSetting> settings);

    <S extends NotificationSetting> List<S> saveAll(Iterable<S> settings);
}
