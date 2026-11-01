package com.moewadeul.common.testsupport;

import jakarta.servlet.http.Cookie;
import java.util.Arrays;
import java.util.UUID;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

public final class TestCsrf {

    private static final String COOKIE_NAME = "XSRF-TOKEN";
    private static final String HEADER_NAME = "X-XSRF-TOKEN";

    private TestCsrf() {}

    public static RequestPostProcessor csrf() {
        String token = UUID.randomUUID().toString();
        return request -> {
            request.setCookies(appendCookie(request.getCookies(), new Cookie(COOKIE_NAME, token)));
            request.addHeader(HEADER_NAME, token);
            return request;
        };
    }

    private static Cookie[] appendCookie(Cookie[] existing, Cookie added) {
        if (existing == null) {
            return new Cookie[] {added};
        }
        Cookie[] merged = Arrays.copyOf(existing, existing.length + 1);
        merged[existing.length] = added;
        return merged;
    }
}
