package com.moewadeul.notification.infra.setting;

import com.moewadeul.notification.domain.setting.NotificationSetting;
import com.moewadeul.notification.domain.setting.NotificationSettingId;
import com.moewadeul.notification.domain.setting.NotificationSettingRepository;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NotificationSettingJpaRepository
        extends JpaRepository<NotificationSetting, NotificationSettingId>, NotificationSettingRepository {

    @Override
    List<NotificationSetting> findByMemberId(long memberId);

    @Override
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM NotificationSetting s WHERE s.memberId = :memberId")
    void deleteByMemberId(@Param("memberId") long memberId);

    @Override
    <S extends NotificationSetting> List<S> saveAll(Iterable<S> settings);
}
