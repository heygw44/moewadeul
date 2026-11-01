package com.moewadeul.common.web;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("moewadeul.web")
public record WebProperties(String baseUrl) {

    public WebProperties {
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalArgumentException("moewadeul.web.baseUrl must not be blank");
        }
        baseUrl = stripTrailingSlashes(baseUrl);
    }

    private static String stripTrailingSlashes(String url) {
        int end = url.length();
        while (end > 0 && url.charAt(end - 1) == '/') {
            end--;
        }
        return url.substring(0, end);
    }
}
