package com.moewadeul.member.application.password;

import com.moewadeul.common.mail.MailMessage;
import java.util.List;
import java.util.regex.Pattern;

public final class PasswordResetMails {

    private static final Pattern TOKEN = Pattern.compile("#token=([A-Za-z0-9_-]{43})");

    private PasswordResetMails() {}

    public static String extractToken(MailMessage mail) {
        List<String> tokens = TOKEN.matcher(mail.body())
                .results()
                .map(result -> result.group(1))
                .toList();
        if (tokens.size() != 1) {
            throw new AssertionError("메일 본문에 재설정 토큰이 정확히 하나여야 합니다. 찾은 개수=" + tokens.size());
        }
        return tokens.get(0);
    }
}
