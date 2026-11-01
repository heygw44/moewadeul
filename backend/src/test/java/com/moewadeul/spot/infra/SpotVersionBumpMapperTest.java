package com.moewadeul.spot.infra;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

class SpotVersionBumpMapperTest {

    private static final Pattern UPDATES_SPOT = Pattern.compile("\\bUPDATE\\s+spot\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern BUMPS_VERSION =
            Pattern.compile("\\b(\\w+\\.)?version\\s*=\\s*(\\w+\\.)?version\\s*\\+\\s*1\\b", Pattern.CASE_INSENSITIVE);

    @Test
    @DisplayName("[BC-04] spot 행을 고치는 MyBatis UPDATE는 모두 version을 1 올린다")
    void everySpotUpdateStatementBumpsVersion() throws Exception {
        // given
        List<String> spotUpdates = new ArrayList<>();
        List<String> missingBump = new ArrayList<>();

        // when
        for (Resource resource : new PathMatchingResourcePatternResolver().getResources("classpath*:mapper/**/*.xml")) {
            Document mapper = parse(resource);
            String namespace = mapper.getDocumentElement().getAttribute("namespace");
            NodeList updates = mapper.getElementsByTagName("update");
            for (int i = 0; i < updates.getLength(); i++) {
                Element update = (Element) updates.item(i);
                String sql = update.getTextContent();
                if (!UPDATES_SPOT.matcher(sql).find()) {
                    continue;
                }
                String statement = namespace + "." + update.getAttribute("id");
                spotUpdates.add(statement);
                if (!BUMPS_VERSION.matcher(sql).find()) {
                    missingBump.add(statement);
                }
            }
        }

        // then
        assertThat(spotUpdates).isNotEmpty();
        assertThat(missingBump)
                .as("spot을 고치는 UPDATE가 version = version + 1을 하지 않아 JPA 낙관적 잠금이 바뀐 행을 못 알아챈다")
                .isEmpty();
    }

    private static Document parse(Resource resource) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        DocumentBuilder builder = factory.newDocumentBuilder();
        try (InputStream in = resource.getInputStream()) {
            return builder.parse(in);
        }
    }
}
