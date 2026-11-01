package com.moewadeul.publicdata.infra.gocamping;

import static com.moewadeul.common.external.PublicDataHttp.textOf;

import com.moewadeul.common.external.ExternalApiMetrics;
import com.moewadeul.common.external.PublicDataHttp;
import com.moewadeul.common.external.PublicDataHttp.RawResponse;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@Slf4j
@Component
public class GoCampingClient {

    private static final String API = "gocamping";
    private static final String BASED_SYNC_LIST = "basedSyncList";
    private static final String BASED_SYNC_LIST_URI = "/basedSyncList?serviceKey={serviceKey}&MobileOS={mobileOs}"
            + "&MobileApp={mobileApp}&_type={type}&pageNo={pageNo}&numOfRows={numOfRows}";
    private static final String MOBILE_OS = "ETC";
    private static final String MOBILE_APP = "moewadeul";
    private static final String RESPONSE_TYPE = "json";
    private static final String SUCCESS_RESULT_CODE = "0000";

    private final RestClient restClient;
    private final String serviceKey;
    private final JsonMapper jsonMapper;
    private final ExternalApiMetrics metrics;

    public GoCampingClient(GoCampingProperties properties, JsonMapper jsonMapper, ExternalApiMetrics metrics) {
        this.restClient = PublicDataHttp.createRestClient(
                properties.baseUrl(), properties.connectTimeout(), properties.readTimeout());
        this.serviceKey = properties.serviceKey();
        this.jsonMapper = jsonMapper;
        this.metrics = metrics;
    }

    // 공공데이터포털은 하루 1,000번까지 받고 재시도한 요청도 한도에서 뺀다. 그래서 다시 보내면 성공할 수 있는 실패에만 한 번 더 보낸다.
    public GoCampingPage fetchPage(int pageNo, int numOfRows) {
        return PublicDataHttp.retryOnce(
                () -> fetchBasedSyncListOnce(pageNo, numOfRows),
                e -> log.warn("고캠핑 API 호출이 실패해서 한 번 다시 보낸다. reason={}", e.getMessage()));
    }

    private GoCampingPage fetchBasedSyncListOnce(int pageNo, int numOfRows) {
        return metrics.record(API, BASED_SYNC_LIST, () -> {
            RawResponse response = sendBasedSyncList(pageNo, numOfRows);
            return toPage(response, pageNo, numOfRows);
        });
    }

    private RawResponse sendBasedSyncList(int pageNo, int numOfRows) {
        Map<String, Object> uriVariables = Map.of(
                "serviceKey", serviceKey,
                "mobileOs", MOBILE_OS,
                "mobileApp", MOBILE_APP,
                "type", RESPONSE_TYPE,
                "pageNo", pageNo,
                "numOfRows", numOfRows);
        return PublicDataHttp.get(
                restClient,
                BASED_SYNC_LIST_URI,
                uriVariables,
                (reason, cause) -> GoCampingApiException.retryable(describe(pageNo, reason), cause));
    }

    private GoCampingPage toPage(RawResponse response, int pageNo, int numOfRows) {
        if (response.status().is5xxServerError()) {
            throw GoCampingApiException.retryable(
                    describe(pageNo, "HTTP " + response.status().value()));
        }
        if (PublicDataHttp.looksLikeXml(response.body())) {
            throw new GoCampingApiException(describe(
                    pageNo, "HTTP " + response.status().value() + ", " + PublicDataHttp.xmlErrorOf(response.body())));
        }
        if (!response.status().is2xxSuccessful()) {
            throw new GoCampingApiException(
                    describe(pageNo, "HTTP " + response.status().value()));
        }
        return parse(response.body(), pageNo, numOfRows);
    }

    private GoCampingPage parse(String body, int pageNo, int numOfRows) {
        JsonNode root = readTree(body, pageNo);
        JsonNode header = root.path("response").path("header");
        String resultCode = header.path("resultCode").asString(null);
        if (!SUCCESS_RESULT_CODE.equals(resultCode)) {
            String resultMsg = header.path("resultMsg").asString(null);
            throw new GoCampingApiException(describe(pageNo, "resultCode=" + resultCode + ", resultMsg=" + resultMsg));
        }
        JsonNode responseBody = root.path("response").path("body");
        int totalCount = responseBody.path("totalCount").asInt(-1);
        if (totalCount < 0) {
            throw new GoCampingApiException(describe(pageNo, "응답에 totalCount가 없다"));
        }
        return new GoCampingPage(
                totalCount,
                responseBody.path("pageNo").asInt(pageNo),
                responseBody.path("numOfRows").asInt(numOfRows),
                PublicDataHttp.itemsOf(
                                responseBody.path("items"),
                                reason -> new GoCampingApiException(describe(pageNo, reason)))
                        .stream()
                        .map(GoCampingClient::toItem)
                        .toList());
    }

    private JsonNode readTree(String body, int pageNo) {
        try {
            return jsonMapper.readTree(body);
        } catch (JacksonException e) {
            throw new GoCampingApiException(describe(pageNo, "응답 JSON을 해석하지 못했다"), e);
        }
    }

    private static GoCampingItem toItem(JsonNode node) {
        return new GoCampingItem(
                textOf(node, "contentId"),
                textOf(node, "facltNm"),
                textOf(node, "addr1"),
                textOf(node, "addr2"),
                textOf(node, "mapX"),
                textOf(node, "mapY"),
                textOf(node, "induty"),
                textOf(node, "tel"),
                textOf(node, "homepage"),
                textOf(node, "toiletCo"),
                textOf(node, "swrmCo"),
                textOf(node, "wtrplCo"),
                textOf(node, "brazierCl"),
                textOf(node, "sbrsCl"),
                textOf(node, "sbrsEtc"),
                textOf(node, "posblFcltyCl"),
                textOf(node, "posblFcltyEtc"),
                textOf(node, "animalCmgCl"),
                textOf(node, "syncStatus"),
                textOf(node, "manageSttus"),
                textOf(node, "hvofBgnde"),
                textOf(node, "hvofEnddle"),
                textOf(node, "modifiedtime"),
                textOf(node, "lctCl"));
    }

    private static String describe(int pageNo, String reason) {
        return "고캠핑 " + BASED_SYNC_LIST + " 호출 실패 pageNo=" + pageNo + ": " + reason;
    }
}
