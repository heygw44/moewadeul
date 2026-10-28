package com.moewadeul.notification.domain.setting;

import java.io.Serializable;

public record NotificationSettingId(long memberId, String type) implements Serializable {}
