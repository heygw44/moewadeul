package com.moewadeul.aisearch.infra;

import com.moewadeul.aisearch.application.AiSpotSetting;
import com.moewadeul.aisearch.application.AiSpotType;
import com.moewadeul.aisearch.application.SpotQueryConditions;
import com.moewadeul.aisearch.application.SpotQueryInterpretation;
import com.moewadeul.aisearch.application.SpotQueryInterpretationException;
import com.moewadeul.common.external.ExternalApiMetrics;
import com.moewadeul.common.external.ExternalApiProperties;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * 실제 OpenAI를 호출해 고정된 문장 30개의 해석 정확도를 잰다. 비용이 들어서 CI에서는 돌리지 않고,
 * {@code OPENAI_API_KEY}를 넣고 {@code ./gradlew aiSearchEval}로 직접 실행한다. 틀려도 테스트는 실패하지 않고 결과만 출력한다.
 */
@Tag("ai-eval")
class AiSearchEvalTest {

    private static final List<String> FIELDS = List.of(
            "region",
            "radiusKm",
            "types",
            "hasWater",
            "hasToilet",
            "excludeWarning",
            "hideClosed",
            "settings",
            "unparsed");

    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    @Test
    @DisplayName("[F-27] 평가 문장 30개를 실제 OpenAI로 해석해 정확도를 출력한다")
    void evaluate() throws IOException {
        String apiKey = System.getenv("OPENAI_API_KEY");
        Assumptions.assumeTrue(apiKey != null && !apiKey.isBlank(), "OPENAI_API_KEY가 없어서 평가를 건너뛴다");
        OpenAiProperties properties = new OpenAiProperties(
                "https://api.openai.com/v1", apiKey, "gpt-4.1-mini", Duration.ofSeconds(1), Duration.ofSeconds(5), 300);
        OpenAiSpotQueryInterpreter interpreter = new OpenAiSpotQueryInterpreter(
                properties,
                new ExternalApiProperties(10),
                jsonMapper,
                new ExternalApiMetrics(new SimpleMeterRegistry()));

        List<JsonNode> cases = loadCases();
        Map<String, Integer> fieldCorrect = new LinkedHashMap<>();
        FIELDS.forEach(field -> fieldCorrect.put(field, 0));
        int correctSentences = 0;
        long inputTokens = 0;
        long outputTokens = 0;
        for (JsonNode expected : cases) {
            String sentence = expected.get("sentence").asString();
            try {
                SpotQueryInterpretation result = interpreter.interpret(sentence);
                inputTokens += result.usage().inputTokens();
                outputTokens += result.usage().outputTokens();
                List<String> differences = compare(expected, result.conditions());
                FIELDS.stream()
                        .filter(field -> !differences.contains(field))
                        .forEach(field -> fieldCorrect.merge(field, 1, Integer::sum));
                correctSentences += differences.isEmpty() ? 1 : 0;
                System.out.println((differences.isEmpty() ? "✓ " : "✗ ")
                        + sentence
                        + (differences.isEmpty()
                                ? ""
                                : "  다른 필드: " + differences + "  기대: " + expected + "  실제: " + result.conditions()));
            } catch (SpotQueryInterpretationException e) {
                System.out.println("✗ " + sentence + "  호출 실패: " + e.getMessage());
            }
        }

        System.out.println("정확한 문장: " + correctSentences + " / " + cases.size());
        fieldCorrect.forEach((field, count) -> System.out.println("  " + field + ": " + count + " / " + cases.size()));
        System.out.println("토큰 합계: 입력 " + inputTokens + ", 출력 " + outputTokens);
    }

    private List<JsonNode> loadCases() throws IOException {
        try (InputStream in = new ClassPathResource("aisearch/eval-set.json").getInputStream()) {
            return new ArrayList<>(jsonMapper.readTree(in).values().stream().toList());
        }
    }

    private List<String> compare(JsonNode expected, SpotQueryConditions actual) {
        List<String> differences = new ArrayList<>();
        if (!regionMatches(expected.path("region").asString(null), actual.region())) {
            differences.add("region");
        }
        Double expectedRadius =
                expected.has("radiusKm") ? expected.get("radiusKm").asDouble() : null;
        if (!java.util.Objects.equals(expectedRadius, actual.radiusKm())) {
            differences.add("radiusKm");
        }
        if (!enumSet(expected.path("types"), AiSpotType::valueOf).equals(actual.types())) {
            differences.add("types");
        }
        addIfDifferent(differences, "hasWater", expected.path("hasWater").asBoolean(false), actual.hasWater());
        addIfDifferent(differences, "hasToilet", expected.path("hasToilet").asBoolean(false), actual.hasToilet());
        addIfDifferent(
                differences,
                "excludeWarning",
                expected.path("excludeWarning").asBoolean(false),
                actual.excludeWarning());
        addIfDifferent(differences, "hideClosed", expected.path("hideClosed").asBoolean(false), actual.hideClosed());
        if (!enumSet(expected.path("settings"), AiSpotSetting::valueOf).equals(actual.settings())) {
            differences.add("settings");
        }
        addIfDifferent(
                differences,
                "unparsed",
                expected.path("unparsedExpected").asBoolean(false),
                !actual.unparsed().isEmpty());
        return differences;
    }

    private static void addIfDifferent(List<String> differences, String field, boolean expected, boolean actual) {
        if (expected != actual) {
            differences.add(field);
        }
    }

    // 지역은 표기가 조금씩 달라질 수 있어서(예: "홍천" / "홍천군") 공백을 뺀 뒤 한쪽이 다른 쪽을 포함하면 맞는 것으로 본다.
    private static boolean regionMatches(String expected, String actual) {
        if (expected == null || actual == null) {
            return expected == null && actual == null;
        }
        String a = expected.replace(" ", "");
        String b = actual.replace(" ", "");
        return a.contains(b) || b.contains(a);
    }

    private static <E> Set<E> enumSet(JsonNode node, java.util.function.Function<String, E> parser) {
        return node.values().stream()
                .map(value -> parser.apply(value.asString()))
                .collect(Collectors.toSet());
    }
}
