package com.moewadeul.notification.api.setting;

import com.moewadeul.notification.application.setting.NotificationSettingItem;

record NotificationSettingResponse(String type, boolean emailEnabled) {

    static NotificationSettingResponse from(NotificationSettingItem item) {
        return new NotificationSettingResponse(item.type(), item.emailEnabled());
    }
}
