package com.moewadeul.member.infra.signup;

import static org.assertj.core.api.Assertions.assertThat;

import com.moewadeul.common.testsupport.IntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

@IntegrationTest
class DisposableEmailDomainColumnLengthIntegrationTest {

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    @DisplayName("목록 파일이 걸러 내는 도메인 최대 길이는 disposable_email_domain.domain 컬럼 길이와 같다")
    void maxDomainLengthEqualsColumnLength() {
        // when
        Integer columnLength = jdbc.queryForObject(
                "SELECT CHARACTER_MAXIMUM_LENGTH FROM information_schema.COLUMNS"
                        + " WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'disposable_email_domain'"
                        + " AND COLUMN_NAME = 'domain'",
                Integer.class);

        // then
        assertThat(DisposableEmailDomainListFile.MAX_DOMAIN_LENGTH).isEqualTo(columnLength);
    }
}
