package com.moewadeul.community.api.post;

import static com.moewadeul.community.api.CommunityApiFixture.POSTS;
import static org.assertj.core.api.Assertions.assertThat;

import com.moewadeul.community.api.CommunityApiFixture;
import jakarta.servlet.http.Cookie;
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

    void like(long postId, List<Cookie> sessions) {
        for (Cookie session : sessions) {
            assertThat(fixture.send(mvc.put().uri(POSTS + "/" + postId + "/like"), session, null))
                    .hasStatus(HttpStatus.OK);
        }
    }
}
