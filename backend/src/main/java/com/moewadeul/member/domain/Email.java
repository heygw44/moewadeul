package com.moewadeul.member.domain;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

public final class Email {

    private static final char AT = '@';
    private static final char DOT = '.';
    private static final char IP_LITERAL_START = '[';
    private static final char FIRST_PRINTABLE_ASCII = '!';
    private static final char LAST_PRINTABLE_ASCII = '~';

    private final String value;

    private Email(String value) {
        this.value = value;
    }

    public static Email of(String raw) {
        if (raw == null) {
            throw new IllegalArgumentException("이메일이 null입니다.");
        }
        boolean acceptable = isAsciiAddress(raw)
                && domainOf(raw).filter(Email::isMailableDomain).isPresent();
        if (!acceptable) {
            throw new IllegalArgumentException("이메일 형식이 올바르지 않습니다.");
        }
        return new Email(raw.toLowerCase(Locale.ROOT));
    }

    public static Optional<String> domainOf(String raw) {
        int at = raw.lastIndexOf(AT);
        if (at <= 0 || at == raw.length() - 1) {
            return Optional.empty();
        }
        return Optional.of(raw.substring(at + 1));
    }

    public static boolean isMailableDomain(String domain) {
        return domain.indexOf(DOT) >= 0 && domain.indexOf(IP_LITERAL_START) < 0;
    }

    /**
     * DB의 이메일 비교(대소문자만 구분 없음)는 전각 문자({@code ｖ})와
     * 보이지 않는 문자(zero-width space, soft hyphen)를 같은 글자로 본다. 그래서 이런 변형으로 같은 회원을 가리키면서 이메일마다 세는
     * 요청 한도를 피할 수 있다.
     */
    public static boolean isAsciiAddress(String raw) {
        return raw.chars().allMatch(c -> c >= FIRST_PRINTABLE_ASCII && c <= LAST_PRINTABLE_ASCII);
    }

    public String value() {
        return value;
    }

    public String domain() {
        return value.substring(value.lastIndexOf(AT) + 1);
    }

    public List<String> domainCandidates() {
        List<String> candidates = new ArrayList<>();
        String current = domain();
        while (current.indexOf(DOT) >= 0) {
            candidates.add(current);
            current = current.substring(current.indexOf(DOT) + 1);
        }
        return candidates;
    }

    @Override
    public boolean equals(Object o) {
        return this == o || (o instanceof Email other && value.equals(other.value));
    }

    @Override
    public int hashCode() {
        return value.hashCode();
    }

    @Override
    public String toString() {
        return "Email[***]";
    }
}
