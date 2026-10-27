package com.moewadeul.member.application.verification;

import com.moewadeul.common.mail.MailMessage;
import java.util.List;
import java.util.regex.MatchResult;
import java.util.regex.Pattern;

public final class VerificationMails {

    private static final Pattern CODE = Pattern.compile("(?<!\\d)\\d{6}(?!\\d)");

    private VerificationMails() {}

    public static String extractCode(MailMessage mail) {
        List<String> codes =
                CODE.matcher(mail.body()).results().map(MatchResult::group).toList();
        if (codes.size() != 1) {
            throw new AssertionError("메일 본문에 6자리 코드가 정확히 하나여야 합니다. 찾은 개수=" + codes.size());
        }
        return codes.get(0);
    }
}
