package com.moewadeul.notification.infra.setting;

import com.moewadeul.notification.domain.setting.NotificationSetting;
import com.moewadeul.notification.domain.setting.NotificationSettingId;
import com.moewadeul.notification.domain.setting.NotificationSettingRepository;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationSettingJpaRepository
        extends JpaRepository<NotificationSetting, NotificationSettingId>, NotificationSettingRepository {

    @Override
    List<NotificationSetting> findByMemberId(long memberId);

    @Override
    void deleteAll(Iterable<? extends NotificationSetting> settings);

    @Override
    <S extends NotificationSetting> List<S> saveAll(Iterable<S> settings);
}
