package com.moewadeul.common.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

class SpotVersionBumpTest {

    private static final Pattern UPDATE_STATEMENT =
            Pattern.compile("<update\\s+id=\"([^\"]+)\"[^>]*>(.*?)</update>", Pattern.DOTALL);
    private static final Pattern UPDATES_SPOT = Pattern.compile("\\bUPDATE\\s+spot\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern BUMPS_VERSION =
            Pattern.compile("\\bversion\\s*=\\s*(?:\\w+\\.)?version\\s*\\+\\s*1", Pattern.CASE_INSENSITIVE);

    @Test
    @DisplayName("spot을 고치는 매퍼 SQL은 version을 올려 엔티티의 번호 검사가 그 변경을 알아채게 한다")
    void everySpotUpdateBumpsVersion() throws IOException {
        List<String> checked = new ArrayList<>();
        List<String> missing = new ArrayList<>();
        Resource[] mappers = new PathMatchingResourcePatternResolver().getResources("classpath*:mapper/**/*.xml");
        for (Resource mapper : mappers) {
            Matcher statement = UPDATE_STATEMENT.matcher(mapper.getContentAsString(StandardCharsets.UTF_8));
            while (statement.find()) {
                if (!UPDATES_SPOT.matcher(statement.group(2)).find()) {
                    continue;
                }
                String name = mapper.getFilename() + "#" + statement.group(1);
                checked.add(name);
                if (!BUMPS_VERSION.matcher(statement.group(2)).find()) {
                    missing.add(name);
                }
            }
        }

        assertThat(checked).as("검사한 문장이 없으면 패턴이 깨진 것이다").isNotEmpty();
        assertThat(missing).as("version을 올리지 않는 spot UPDATE").isEmpty();
    }
}
