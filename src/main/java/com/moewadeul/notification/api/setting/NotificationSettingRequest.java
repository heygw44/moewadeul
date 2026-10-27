package com.moewadeul.notification.api.setting;

import com.moewadeul.notification.application.setting.NotificationSettingItem;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

record NotificationSettingRequest(
        @NotBlank(message = "알림 종류를 입력해 주세요.") String type,
        @NotNull(message = "이메일 수신 여부를 입력해 주세요.") Boolean emailEnabled) {

    NotificationSettingItem toItem() {
        return new NotificationSettingItem(type, emailEnabled);
    }
}
