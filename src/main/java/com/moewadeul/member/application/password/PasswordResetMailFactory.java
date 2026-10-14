package com.moewadeul.member.application.password;

import com.moewadeul.common.mail.MailMessage;
import com.moewadeul.member.domain.password.PasswordResetPolicy;

/** 비밀번호 재설정 링크를 담은 메일을 만든다. 토큰은 이 메일 본문의 링크에만 들어간다. */
final class PasswordResetMailFactory {

    static final String SUBJECT = "[뫼와들] 비밀번호 재설정 안내";

    private static final String BODY_TEMPLATE = """
            안녕하세요, 뫼와들이에요.

            아래 링크에서 새 비밀번호를 설정해 주세요.

            %s

            이 링크는 %d분 동안 한 번만 쓸 수 있어요.

            직접 요청하지 않았다면 이 메일은 무시해 주세요. 비밀번호는 바뀌지 않아요.
            """;

    private PasswordResetMailFactory() {}

    static MailMessage create(String email, String link) {
        long minutes = PasswordResetPolicy.TOKEN_VALIDITY.toMinutes();
        return new MailMessage(email, SUBJECT, BODY_TEMPLATE.formatted(link, minutes));
    }
}
