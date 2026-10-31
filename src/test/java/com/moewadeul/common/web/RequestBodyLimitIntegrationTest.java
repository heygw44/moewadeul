package com.moewadeul.common.web;

import static com.moewadeul.common.testsupport.TestCsrf.csrf;
import static org.assertj.core.api.Assertions.assertThat;

import com.moewadeul.common.testsupport.IntegrationTest;
import com.moewadeul.common.testsupport.TestSequence;
import java.util.Collections;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

@IntegrationTest
@AutoConfigureMockMvc
class RequestBodyLimitIntegrationTest {

    private static final int MAX_STRING_LENGTH = 100_000;
    private static final int MAX_DOCUMENT_LENGTH = 1_000_000;

    @Autowired
    private MockMvcTester mvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    @DisplayName("문자열 상한까지인 비밀번호는 읽어서 로그인 실패(401)로 끝난다")
    void passwordAtStringLimitIsRead() {
        MvcTestResult result = login("{\"email\":\"%s\",\"password\":\"%s\"}"
                .formatted(TestSequence.email(), "A".repeat(MAX_STRING_LENGTH)));

        assertThat(result).hasStatus(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("문자열 상한을 넘는 비밀번호는 400 INVALID_INPUT으로 끝나고 로그인 시도를 기록하지 않는다")
    void passwordOverStringLimitIsRejected() {
        MvcTestResult result = login("{\"email\":\"%s\",\"password\":\"%s\"}"
                .formatted(TestSequence.email(), "A".repeat(MAX_STRING_LENGTH + 1)));

        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("INVALID_INPUT");
        assertThat(loginHistoryCount()).isZero();
    }

    @Test
    @DisplayName("문자열은 상한 안이어도 본문이 문서 상한을 넘으면 400 INVALID_INPUT으로 끝난다")
    void bodyOverDocumentLimitIsRejected() {
        String chunk = "\"" + "A".repeat(MAX_STRING_LENGTH - 10) + "\"";
        String padding = String.join(",", Collections.nCopies(MAX_DOCUMENT_LENGTH / chunk.length() + 1, chunk));
        String body = "{\"email\":\"%s\",\"password\":\"Valid-pass1\",\"padding\":[%s]}"
                .formatted(TestSequence.email(), padding);
        assertThat(body.length()).isGreaterThan(MAX_DOCUMENT_LENGTH);

        MvcTestResult result = login(body);

        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("INVALID_INPUT");
        assertThat(loginHistoryCount()).isZero();
    }

    private MvcTestResult login(String body) {
        return mvc.post()
                .uri("/api/auth/login")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .exchange();
    }

    private int loginHistoryCount() {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM login_history", Integer.class);
        return count == null ? 0 : count;
    }
}
