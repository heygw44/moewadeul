package com.moewadeul.notification.application.event;

import com.moewadeul.common.mail.MailMessage;
import com.moewadeul.notification.infra.event.NotificationMailRow;

final class NotificationMailFactory {

    private static final String SUBJECT_PREFIX = "[뫼와들] ";
    private static final String LINK_LABEL = "자세히 보기: ";

    private NotificationMailFactory() {}

    static MailMessage create(NotificationMailRow row, String baseUrl) {
        String body = row.link() == null
                ? row.body()
                : row.body() + "\n\n" + LINK_LABEL + stripTrailingSlashes(baseUrl) + row.link();
        return new MailMessage(row.email(), SUBJECT_PREFIX + row.title(), body);
    }

    private static String stripTrailingSlashes(String url) {
        int end = url.length();
        while (end > 0 && url.charAt(end - 1) == '/') {
            end--;
        }
        return url.substring(0, end);
    }
}
