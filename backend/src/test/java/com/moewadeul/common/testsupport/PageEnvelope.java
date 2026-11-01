package com.moewadeul.common.testsupport;

import static org.assertj.core.api.Assertions.assertThat;

import org.springframework.test.web.servlet.assertj.MvcTestResult;

public final class PageEnvelope {

    private PageEnvelope() {}

    public static void assertEnvelope(MvcTestResult result, int page, int size, boolean hasNext) {
        assertThat(result)
                .bodyJson()
                .extractingPath("$")
                .asMap()
                .containsOnlyKeys("content", "page", "size", "hasNext");
        assertThat(result).bodyJson().extractingPath("$.content").asList();
        assertThat(result).bodyJson().extractingPath("$.page").isEqualTo(page);
        assertThat(result).bodyJson().extractingPath("$.size").isEqualTo(size);
        assertThat(result).bodyJson().extractingPath("$.hasNext").isEqualTo(hasNext);
    }
}
