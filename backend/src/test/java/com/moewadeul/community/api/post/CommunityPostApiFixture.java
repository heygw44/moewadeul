package com.moewadeul.community.api.post;

import static com.moewadeul.community.api.CommunityApiFixture.POSTS;
import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import com.moewadeul.community.api.CommunityApiFixture;
import jakarta.servlet.http.Cookie;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.assertj.core.api.AbstractListAssert;
import org.assertj.core.api.ObjectAssert;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

final class CommunityPostApiFixture {

    private final MockMvcTester mvc;
    private final CommunityApiFixture fixture;

    CommunityPostApiFixture(MockMvcTester mvc, CommunityApiFixture fixture) {
        this.mvc = mvc;
        this.fixture = fixture;
    }

    static AbstractListAssert<?, List<?>, Object, ObjectAssert<Object>> postIds(MvcTestResult result) {
        assertThat(result).hasStatus(HttpStatus.OK);
        return assertThat(result)
                .bodyJson()
                .extractingPath("$.content[*].postId")
                .asList();
    }

    static Long nextCursor(MvcTestResult result) {
        assertThat(result).hasStatus(HttpStatus.OK);
        String body = new String(result.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8);
        Number cursor = JsonPath.read(body, "$.nextCursor");
        return cursor == null ? null : cursor.longValue();
    }

    void like(long postId, List<Cookie> sessions) {
        for (Cookie session : sessions) {
            assertThat(fixture.send(mvc.put().uri(POSTS + "/" + postId + "/like"), session, null))
                    .hasStatus(HttpStatus.OK);
        }
    }
}
