package com.moewadeul.weather.infra;

import com.moewadeul.common.external.ExternalApiMetrics;
import com.moewadeul.common.external.ExternalApiProperties;
import com.moewadeul.common.external.ExternalCallLimit;
import com.moewadeul.common.external.ExternalFailureCooldown;
import java.io.IOException;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.DefaultUriBuilderFactory;
import org.springframework.web.util.DefaultUriBuilderFactory.EncodingMode;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@Slf4j
@Component
public class KmaClient {

    private static final String API = "kma";
    private static final String VILAGE_FCST = "getVilageFcst";
    private static final String MID_LAND_FCST = "getMidLandFcst";
    private static final String MID_TA = "getMidTa";

    private static final String COMMON_QUERY =
            "serviceKey={serviceKey}&pageNo={pageNo}&numOfRows={numOfRows}&dataType={dataType}";
    private static final String VILAGE_FCST_URI = "/VilageFcstInfoService_2.0/getVilageFcst?" + COMMON_QUERY
            + "&base_date={baseDate}&base_time={baseTime}&nx={nx}&ny={ny}";
    private static final String MID_LAND_FCST_URI =
            "/MidFcstInfoService/getMidLandFcst?" + COMMON_QUERY + "&regId={regId}&tmFc={tmFc}";
    private static final String MID_TA_URI =
            "/MidFcstInfoService/getMidTa?" + COMMON_QUERY + "&regId={regId}&tmFc={tmFc}";

    private static final int PAGE_NO = 1;
    // 단기예보는 한 발표에 격자 하나의 항목이 800~1,050건쯤이다. 발표 시각에 따라 달라서, 2026-10-10에 잰 값은 14시 발표가 798건으로
    // 가장 적고 17시 발표가 1,052건으로 가장 많았다. 호출 한도를 아끼려고 클라이언트는 한 페이지로 다 받는다. 그래서 행 수는 최댓값보다 넉넉히 잡는다.
    private static final int VILAGE_FCST_ROWS = 1500;
    private static final int MID_FCST_ROWS = 10;
    private static final String RESPONSE_TYPE = "JSON";
    private static final String SUCCESS_RESULT_CODE = "00";
    private static final DateTimeFormatter BASE_DATE = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final DateTimeFormatter BASE_TIME = DateTimeFormatter.ofPattern("HHmm");
    private static final DateTimeFormatter TM_FC = DateTimeFormatter.ofPattern("yyyyMMddHHmm");
    private static final Pattern AUTH_ERROR_MESSAGE =
            Pattern.compile("<returnAuthMsg>\\s*([^<]*?)\\s*</returnAuthMsg>");
    private static final Pattern ERROR_MESSAGE = Pattern.compile("<errMsg>\\s*([^<]*?)\\s*</errMsg>");

    private final RestClient restClient;
    private final String serviceKey;
    private final JsonMapper jsonMapper;
    private final ExternalApiMetrics metrics;
    private final ExternalCallLimit callLimit;
    private final ExternalFailureCooldown failureCooldown;

    public KmaClient(
            KmaProperties properties,
            ExternalApiProperties externalApiProperties,
            JsonMapper jsonMapper,
            ExternalApiMetrics metrics,
            Clock clock) {
        this.restClient = createRestClient(properties);
        this.serviceKey = properties.serviceKey();
        this.jsonMapper = jsonMapper;
        this.metrics = metrics;
        this.callLimit = new ExternalCallLimit(API, externalApiProperties, metrics);
        this.failureCooldown = new ExternalFailureCooldown(API, externalApiProperties, clock, metrics);
    }

    public List<KmaShortTermItem> getVilageFcst(LocalDateTime baseDateTime, int nx, int ny) {
        Map<String, Object> variables = baseVariables(VILAGE_FCST_ROWS);
        variables.put("baseDate", BASE_DATE.format(baseDateTime));
        variables.put("baseTime", BASE_TIME.format(baseDateTime));
        variables.put("nx", nx);
        variables.put("ny", ny);
        return fetch(VILAGE_FCST, VILAGE_FCST_URI, variables, this::toShortTermItems);
    }

    public Optional<KmaMidTermItem> getMidLandFcst(String regId, LocalDateTime tmFc) {
        return fetchMid(MID_LAND_FCST, MID_LAND_FCST_URI, regId, tmFc);
    }

    public Optional<KmaMidTermItem> getMidTa(String regId, LocalDateTime tmFc) {
        return fetchMid(MID_TA, MID_TA_URI, regId, tmFc);
    }

    private Optional<KmaMidTermItem> fetchMid(String operation, String uri, String regId, LocalDateTime tmFc) {
        Map<String, Object> variables = baseVariables(MID_FCST_ROWS);
        variables.put("regId", regId);
        variables.put("tmFc", TM_FC.format(tmFc));
        return fetch(operation, uri, variables, body -> toMidTermItem(operation, body));
    }

    private Map<String, Object> baseVariables(int numOfRows) {
        Map<String, Object> variables = new HashMap<>();
        variables.put("serviceKey", serviceKey);
        variables.put("pageNo", PAGE_NO);
        variables.put("numOfRows", numOfRows);
        variables.put("dataType", RESPONSE_TYPE);
        return variables;
    }

    private <T> T fetch(String operation, String uri, Map<String, Object> variables, Function<JsonNode, T> parser) {
        return callLimit.call(
                () -> failureCooldown.call(
                        () -> fetchWithRetry(operation, uri, variables, parser),
                        KmaClient::isOutage,
                        () -> new KmaApiException(describe(operation, "최근 호출이 재시도까지 실패해서 쉬는 중이라 호출하지 않았다"))),
                () -> new KmaApiException(describe(operation, "동시 호출 수가 한도에 닿아 호출하지 않았다")));
    }

    private static boolean isOutage(RuntimeException e) {
        return e instanceof KmaApiException kmaException && kmaException.isRetryable();
    }

    private <T> T fetchWithRetry(
            String operation, String uri, Map<String, Object> variables, Function<JsonNode, T> parser) {
        try {
            return fetchOnce(operation, uri, variables, parser);
        } catch (KmaApiException e) {
            if (!e.isRetryable()) {
                throw e;
            }
            log.warn("기상청 API 호출이 실패해서 한 번 다시 보낸다. reason={}", e.getMessage());
            return fetchOnce(operation, uri, variables, parser);
        }
    }

    private <T> T fetchOnce(String operation, String uri, Map<String, Object> variables, Function<JsonNode, T> parser) {
        return metrics.record(API, operation, () -> {
            RawResponse response = send(operation, uri, variables);
            return parser.apply(parseBody(operation, response));
        });
    }

    private RawResponse send(String operation, String uri, Map<String, Object> variables) {
        try {
            return restClient.get().uri(uri, variables).exchange((request, response) -> read(response));
        } catch (ResourceAccessException e) {
            // Spring은 이 예외 메시지에서 쿼리를 뺀 주소만 남긴다. 그래서 원인으로 이어도 메시지에 키가 들어가지 않는다.
            throw KmaApiException.retryable(
                    describe(
                            operation,
                            "I/O 오류 " + e.getMostSpecificCause().getClass().getSimpleName()),
                    e);
        }
    }

    private static RawResponse read(ClientHttpResponse response) throws IOException {
        String body = StreamUtils.copyToString(response.getBody(), StandardCharsets.UTF_8);
        return new RawResponse(response.getStatusCode(), body);
    }

    private JsonNode parseBody(String operation, RawResponse response) {
        if (response.status().is5xxServerError()) {
            throw KmaApiException.retryable(
                    describe(operation, "HTTP " + response.status().value()));
        }
        if (looksLikeXml(response.body())) {
            throw new KmaApiException(
                    describe(operation, "HTTP " + response.status().value() + ", " + xmlErrorOf(response.body())));
        }
        if (!response.status().is2xxSuccessful()) {
            throw new KmaApiException(
                    describe(operation, "HTTP " + response.status().value()));
        }
        JsonNode root = readTree(operation, response.body());
        JsonNode header = root.path("response").path("header");
        String resultCode = header.path("resultCode").asString(null);
        if (!SUCCESS_RESULT_CODE.equals(resultCode)) {
            String resultMsg = header.path("resultMsg").asString(null);
            throw new KmaApiException(describe(operation, "resultCode=" + resultCode + ", resultMsg=" + resultMsg));
        }
        return root.path("response").path("body");
    }

    private JsonNode readTree(String operation, String body) {
        try {
            return jsonMapper.readTree(body);
        } catch (JacksonException e) {
            throw new KmaApiException(describe(operation, "응답 JSON을 해석하지 못했다"), e);
        }
    }

    private List<KmaShortTermItem> toShortTermItems(JsonNode body) {
        List<JsonNode> items = itemsOf(VILAGE_FCST, body);
        int totalCount = body.path("totalCount").asInt(-1);
        if (totalCount < 0) {
            throw new KmaApiException(describe(VILAGE_FCST, "응답에 totalCount가 없다"));
        }
        if (totalCount > items.size()) {
            throw new KmaApiException(
                    describe(VILAGE_FCST, "한 페이지에 다 담기지 않았다 totalCount=" + totalCount + ", 받은 수=" + items.size()));
        }
        List<KmaShortTermItem> result = new ArrayList<>(items.size());
        for (JsonNode item : items) {
            result.add(new KmaShortTermItem(
                    textOf(item, "category"),
                    textOf(item, "fcstDate"),
                    textOf(item, "fcstTime"),
                    textOf(item, "fcstValue")));
        }
        return result;
    }

    private Optional<KmaMidTermItem> toMidTermItem(String operation, JsonNode body) {
        List<JsonNode> items = itemsOf(operation, body);
        if (items.isEmpty()) {
            return Optional.empty();
        }
        Map<String, String> fields = new HashMap<>();
        for (Map.Entry<String, JsonNode> field : items.getFirst().properties()) {
            String value = field.getValue().asString(null);
            if (value != null && field.getValue().isValueNode()) {
                fields.put(field.getKey(), value);
            }
        }
        return Optional.of(new KmaMidTermItem(fields));
    }

    // 공공데이터포털은 결과가 없으면 items를 빈 문자열("")로 주고, 결과가 한 건이면 item을 배열이 아닌 객체 하나로 줄 수 있다.
    // 그래서 클라이언트는 세 모양(빈 값, 객체 하나, 배열)을 모두 목록으로 바꾼다.
    private List<JsonNode> itemsOf(String operation, JsonNode body) {
        JsonNode items = body.path("items");
        if (items.isMissingNode()
                || items.isNull()
                || (items.isString() && items.asString().isBlank())) {
            return List.of();
        }
        if (!items.isObject()) {
            throw new KmaApiException(describe(operation, "items 형식을 해석하지 못했다"));
        }
        JsonNode item = items.path("item");
        if (item.isMissingNode() || item.isNull()) {
            return List.of();
        }
        if (item.isObject()) {
            return List.of(item);
        }
        if (!item.isArray()) {
            throw new KmaApiException(describe(operation, "item 형식을 해석하지 못했다"));
        }
        List<JsonNode> result = new ArrayList<>(item.size());
        item.values().forEach(result::add);
        return result;
    }

    private static String textOf(JsonNode node, String field) {
        return node.path(field).asString(null);
    }

    private static boolean looksLikeXml(String body) {
        return body.stripLeading().startsWith("<");
    }

    // 인증키가 없거나 등록되지 않았으면, 공공데이터포털 게이트웨이는 dataType=JSON을 무시하고 resultCode 없는 XML을 돌려준다.
    // 예: <OpenAPI_ServiceResponse><cmmMsgHeader><errMsg>SERVICE ERROR</errMsg>
    //
    // <returnAuthMsg>SERVICE_KEY_IS_NOT_REGISTERED_ERROR</returnAuthMsg>...</cmmMsgHeader></OpenAPI_ServiceResponse>
    private static String xmlErrorOf(String body) {
        String authMessage = firstGroup(AUTH_ERROR_MESSAGE, body);
        String errorMessage = firstGroup(ERROR_MESSAGE, body);
        if (authMessage == null && errorMessage == null) {
            return "XML 응답을 해석하지 못했다";
        }
        return "인증 오류 returnAuthMsg=" + authMessage + ", errMsg=" + errorMessage;
    }

    private static String firstGroup(Pattern pattern, String text) {
        Matcher matcher = pattern.matcher(text);
        return matcher.find() ? matcher.group(1) : null;
    }

    private static String describe(String operation, String reason) {
        return "기상청 " + operation + " 호출 실패: " + reason;
    }

    private static RestClient createRestClient(KmaProperties properties) {
        // 공공데이터포털 Decoding 키에는 +, /, = 가 들어 있다. 키를 주소 문자열에 그대로 이어 붙이거나 URI_COMPONENT 방식으로 인코딩하면
        // +가 그대로 나가고, 서버는 +를 공백으로 읽어 인증에 실패한다. 그래서 클라이언트는 키를 URI 변수로 넘기고,
        // URI 변수 값의 예약 문자를 모두 퍼센트 인코딩하는 TEMPLATE_AND_VALUES 방식을 명시해서 고정한다.
        DefaultUriBuilderFactory uriBuilderFactory = new DefaultUriBuilderFactory(properties.baseUrl());
        uriBuilderFactory.setEncodingMode(EncodingMode.TEMPLATE_AND_VALUES);
        // JDK HttpClient는 평문 http 주소에도 HTTP/2 업그레이드를 시도한다. 기상청 API는 HTTP/1.1로 충분하므로 버전을 고정해서 이 협상을 없앤다.
        HttpClient httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(properties.connectTimeout())
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(properties.readTimeout());
        // 압축 응답을 받으면, 응답 대기 시간이 지나 다른 스레드가 본문을 닫는 순간 압축 해제기가 IOException이 아닌 IllegalStateException을
        // 던질 수 있다. 그 예외는 RestClient가 I/O 오류로 감싸지 않아서 이 클라이언트의 실패 처리를 건너뛴다. 그래서 압축을 받지 않는다.
        requestFactory.enableCompression(false);
        return RestClient.builder()
                .uriBuilderFactory(uriBuilderFactory)
                .requestFactory(requestFactory)
                .build();
    }

    private record RawResponse(HttpStatusCode status, String body) {}
}
