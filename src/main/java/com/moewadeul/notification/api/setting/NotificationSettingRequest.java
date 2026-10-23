package com.moewadeul.notification.api.setting;

import com.moewadeul.notification.application.setting.NotificationSettingItem;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** 알림 종류 하나의 이메일 수신 여부를 바꾸는 요청 항목이다. 알림 종류가 맞는지는 서비스가 검사한다. */
record NotificationSettingRequest(
        @NotBlank(message = "알림 종류를 입력해 주세요.") String type,
        @NotNull(message = "이메일 수신 여부를 입력해 주세요.") Boolean emailEnabled) {

    NotificationSettingItem toItem() {
        return new NotificationSettingItem(type, emailEnabled);
    }
}
