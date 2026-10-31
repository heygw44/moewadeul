package com.moewadeul.notification.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class NotificationLabelsDriftTest {

    private static final Path WEB_LABELS = Path.of("web/src/features/notification/notificationLabels.ts");
    private static final Pattern LABEL_KEY = Pattern.compile("^ {2}([A-Z_]+):", Pattern.MULTILINE);

    @Test
    @DisplayName("[F-20][PG-07] 화면 알림 설정의 종류 목록은 서버가 메일 설정을 받는 종류와 같다")
    void webLabelsMatchEmailConfigurableTypes() throws IOException {
        Set<String> webTypes = webLabelKeys();

        assertThat(webTypes)
                .as("web/src/features/notification/notificationLabels.ts의 NOTIFICATION_LABELS와"
                        + " NotificationEventTypes.EMAIL_CONFIGURABLE이 어긋났다. 서버에 종류를 더했다면 화면의"
                        + " NotificationType·NOTIFICATION_LABELS에도 더하고, 화면에만 있다면 서버가 받지 않는 종류이므로 화면에서 뺀다")
                .containsExactlyInAnyOrderElementsOf(NotificationEventTypes.EMAIL_CONFIGURABLE);
    }

    private static Set<String> webLabelKeys() throws IOException {
        String source = Files.readString(WEB_LABELS);
        int start = source.indexOf("NOTIFICATION_LABELS");
        int end = source.indexOf("};", start);
        assertThat(start).as("NOTIFICATION_LABELS를 찾지 못했다").isNotNegative();
        Matcher matcher = LABEL_KEY.matcher(source.substring(start, end));
        Set<String> keys = new LinkedHashSet<>();
        while (matcher.find()) {
            keys.add(matcher.group(1));
        }
        return keys;
    }
}
