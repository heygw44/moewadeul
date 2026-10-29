package com.moewadeul.common.external;

import java.io.IOException;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.util.StreamUtils;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.DefaultUriBuilderFactory;
import org.springframework.web.util.DefaultUriBuilderFactory.EncodingMode;
import tools.jackson.databind.JsonNode;

public final class PublicDataHttp {

    private static final Pattern AUTH_ERROR_MESSAGE =
            Pattern.compile("<returnAuthMsg>\\s*([^<]*?)\\s*</returnAuthMsg>");
    private static final Pattern ERROR_MESSAGE = Pattern.compile("<errMsg>\\s*([^<]*?)\\s*</errMsg>");

    private PublicDataHttp() {}

    public record RawResponse(HttpStatusCode status, String body) {}

    public static RestClient createRestClient(String baseUrl, Duration connectTimeout, Duration readTimeout) {
        // 공공데이터포털 Decoding 키에는 +, /, = 가 들어 있다. 키를 주소 문자열에 그대로 이어 붙이거나 URI_COMPONENT 방식으로 인코딩하면
        // +가 그대로 나가고, 서버는 +를 공백으로 읽어 인증에 실패한다. 그래서 클라이언트는 키를 URI 변수로 넘기고,
        // URI 변수 값의 예약 문자를 모두 퍼센트 인코딩하는 TEMPLATE_AND_VALUES 방식을 명시해서 고정한다.
        DefaultUriBuilderFactory uriBuilderFactory = new DefaultUriBuilderFactory(baseUrl);
        uriBuilderFactory.setEncodingMode(EncodingMode.TEMPLATE_AND_VALUES);
        // JDK HttpClient는 평문 http 주소에도 HTTP/2 업그레이드를 시도한다. 공공데이터포털 API는 HTTP/1.1로 충분하므로 버전을 고정해서 이 협상을 없앤다.
        HttpClient httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(connectTimeout)
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(readTimeout);
        // 압축 응답을 받으면, 응답 대기 시간이 지나 다른 스레드가 본문을 닫는 순간 압축 해제기가 IOException이 아닌 IllegalStateException을
        // 던질 수 있다. 그 예외는 RestClient가 I/O 오류로 감싸지 않아서 클라이언트의 실패 처리를 건너뛴다. 그래서 압축을 받지 않는다.
        requestFactory.enableCompression(false);
        return RestClient.builder()
                .uriBuilderFactory(uriBuilderFactory)
                .requestFactory(requestFactory)
                .build();
    }

    // ioFailure는 I/O 오류 사유와 원인을 받아 클라이언트 고유의 재시도 가능한 예외로 바꾼다.
    public static RawResponse get(
            RestClient restClient,
            String uri,
            Map<String, Object> variables,
            BiFunction<String, ResourceAccessException, ? extends RuntimeException> ioFailure) {
        try {
            return restClient.get().uri(uri, variables).exchange((request, response) -> read(response));
        } catch (ResourceAccessException e) {
            // Spring은 이 예외 메시지에서 쿼리를 뺀 주소만 남긴다. 그래서 원인으로 이어도 메시지에 키가 들어가지 않는다.
            throw ioFailure.apply(
                    "I/O 오류 " + e.getMostSpecificCause().getClass().getSimpleName(), e);
        }
    }

    private static RawResponse read(ClientHttpResponse response) throws IOException {
        String body = StreamUtils.copyToString(response.getBody(), StandardCharsets.UTF_8);
        return new RawResponse(response.getStatusCode(), body);
    }

    public static <T> T retryOnce(Supplier<T> call, Consumer<ExternalApiException> beforeRetry) {
        try {
            return call.get();
        } catch (ExternalApiException e) {
            if (!e.isRetryable()) {
                throw e;
            }
            beforeRetry.accept(e);
            return call.get();
        }
    }

    public static boolean isOutage(RuntimeException e) {
        return e instanceof ExternalApiException apiException && apiException.isRetryable();
    }

    public static boolean looksLikeXml(String body) {
        return body.stripLeading().startsWith("<");
    }

    // 인증키가 없거나 등록되지 않았으면, 공공데이터포털 게이트웨이는 응답 형식 요청(dataType, _type)을 무시하고 resultCode 없는 XML을 돌려준다.
    // 예: <OpenAPI_ServiceResponse><cmmMsgHeader><errMsg>SERVICE ERROR</errMsg>
    // <returnAuthMsg>SERVICE_KEY_IS_NOT_REGISTERED_ERROR</returnAuthMsg>...</cmmMsgHeader></OpenAPI_ServiceResponse>
    public static String xmlErrorOf(String body) {
        String authMessage = firstGroup(AUTH_ERROR_MESSAGE, body);
        String errorMessage = firstGroup(ERROR_MESSAGE, body);
        if (authMessage == null && errorMessage == null) {
            return "XML 응답을 해석하지 못했다";
        }
        return "인증 오류 returnAuthMsg=" + authMessage + ", errMsg=" + errorMessage;
    }

    // 공공데이터포털은 결과가 없으면 items를 빈 문자열("")로 주고, 결과가 한 건이면 item을 배열이 아닌 객체 하나로 줄 수 있다.
    public static List<JsonNode> itemsOf(JsonNode items, Function<String, ? extends RuntimeException> malformed) {
        if (items.isMissingNode()
                || items.isNull()
                || (items.isString() && items.asString().isBlank())) {
            return List.of();
        }
        if (!items.isObject()) {
            throw malformed.apply("items 형식을 해석하지 못했다");
        }
        JsonNode item = items.path("item");
        if (item.isMissingNode() || item.isNull()) {
            return List.of();
        }
        if (item.isObject()) {
            return List.of(item);
        }
        if (!item.isArray()) {
            throw malformed.apply("item 형식을 해석하지 못했다");
        }
        List<JsonNode> result = new ArrayList<>(item.size());
        item.values().forEach(result::add);
        return result;
    }

    public static String textOf(JsonNode node, String field) {
        return node.path(field).asString(null);
    }

    private static String firstGroup(Pattern pattern, String text) {
        Matcher matcher = pattern.matcher(text);
        return matcher.find() ? matcher.group(1) : null;
    }
}
