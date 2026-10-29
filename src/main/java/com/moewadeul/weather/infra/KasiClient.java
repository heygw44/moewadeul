package com.moewadeul.weather.infra;

import com.moewadeul.common.external.ExternalApiMetrics;
import com.moewadeul.common.external.ExternalApiProperties;
import com.moewadeul.common.external.ExternalCallLimit;
import com.moewadeul.common.external.ExternalFailureCooldown;
import com.moewadeul.common.external.PublicDataHttp;
import com.moewadeul.common.external.PublicDataHttp.RawResponse;
import java.io.IOException;
import java.io.StringReader;
import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

@Slf4j
@Component
public class KasiClient {

    private static final String API = "kasi";
    private static final String RISE_SET = "getLCRiseSetInfo";
    private static final String RISE_SET_URI = "/RiseSetInfoService/getLCRiseSetInfo?serviceKey={serviceKey}"
            + "&locdate={locdate}&longitude={longitude}&latitude={latitude}&dnYn={dnYn}";
    private static final String DECIMAL_COORDINATE = "Y";
    private static final String SUCCESS_RESULT_CODE = "00";
    private static final String GATEWAY_ERROR_ROOT = "OpenAPI_ServiceResponse";
    private static final String RESPONSE_ROOT = "response";
    private static final DateTimeFormatter LOCDATE = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final RestClient restClient;
    private final String serviceKey;
    private final ExternalApiMetrics metrics;
    private final ExternalCallLimit callLimit;
    private final ExternalFailureCooldown failureCooldown;

    public KasiClient(
            KasiProperties properties,
            ExternalApiProperties externalApiProperties,
            ExternalApiMetrics metrics,
            Clock clock) {
        this.restClient = PublicDataHttp.createRestClient(
                properties.baseUrl(), properties.connectTimeout(), properties.readTimeout());
        this.serviceKey = properties.serviceKey();
        this.metrics = metrics;
        this.callLimit = new ExternalCallLimit(API, externalApiProperties, metrics);
        this.failureCooldown = new ExternalFailureCooldown(API, externalApiProperties, clock, metrics);
    }

    public Optional<KasiRiseSetItem> getRiseSet(LocalDate date, double lat, double lng) {
        Map<String, Object> variables = new HashMap<>();
        variables.put("serviceKey", serviceKey);
        variables.put("locdate", LOCDATE.format(date));
        variables.put("longitude", Double.toString(lng));
        variables.put("latitude", Double.toString(lat));
        variables.put("dnYn", DECIMAL_COORDINATE);
        return fetch(variables, KasiClient::toItem);
    }

    private <T> T fetch(Map<String, Object> variables, Function<Element, T> parser) {
        return callLimit.call(
                () -> failureCooldown.call(
                        () -> PublicDataHttp.retryOnce(
                                () -> fetchOnce(variables, parser),
                                e -> log.warn("천문연 API 호출이 실패해서 한 번 다시 보낸다. reason={}", e.getMessage())),
                        PublicDataHttp::isOutage,
                        () -> new KasiApiException(describe("최근 호출이 재시도까지 실패해서 쉬는 중이라 호출하지 않았다"))),
                () -> new KasiApiException(describe("동시 호출 수가 한도에 닿아 호출하지 않았다")));
    }

    private <T> T fetchOnce(Map<String, Object> variables, Function<Element, T> parser) {
        return metrics.record(API, RISE_SET, () -> {
            RawResponse response = send(variables);
            return parser.apply(parseBody(response));
        });
    }

    private RawResponse send(Map<String, Object> variables) {
        return PublicDataHttp.get(
                restClient,
                RISE_SET_URI,
                variables,
                (reason, cause) -> KasiApiException.retryable(describe(reason), cause));
    }

    private Element parseBody(RawResponse response) {
        if (response.status().is5xxServerError()) {
            throw KasiApiException.retryable(
                    describe("HTTP " + response.status().value()));
        }
        Element root = parseXml(response);
        if (GATEWAY_ERROR_ROOT.equals(root.getTagName())) {
            throw new KasiApiException(
                    describe("HTTP " + response.status().value() + ", " + PublicDataHttp.xmlErrorOf(response.body())));
        }
        if (!response.status().is2xxSuccessful()) {
            throw new KasiApiException(describe("HTTP " + response.status().value()));
        }
        if (!RESPONSE_ROOT.equals(root.getTagName())) {
            throw new KasiApiException(describe("XML 응답의 형식을 알 수 없다"));
        }
        String resultCode = textOf(root, "resultCode");
        if (!SUCCESS_RESULT_CODE.equals(resultCode)) {
            throw new KasiApiException(
                    describe("resultCode=" + resultCode + ", resultMsg=" + textOf(root, "resultMsg")));
        }
        return root;
    }

    private Element parseXml(RawResponse response) {
        try {
            return newDocumentBuilder()
                    .parse(new InputSource(new StringReader(response.body())))
                    .getDocumentElement();
        } catch (SAXException | IOException e) {
            if (!response.status().is2xxSuccessful()) {
                throw new KasiApiException(describe("HTTP " + response.status().value()), e);
            }
            throw new KasiApiException(describe("응답 XML을 해석하지 못했다"), e);
        }
    }

    private static DocumentBuilder newDocumentBuilder() {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);
            return factory.newDocumentBuilder();
        } catch (ParserConfigurationException e) {
            throw new KasiApiException(describe("XML 파서를 설정하지 못했다"), e);
        }
    }

    private static Optional<KasiRiseSetItem> toItem(Element root) {
        NodeList items = root.getElementsByTagName("item");
        if (items.getLength() == 0) {
            return Optional.empty();
        }
        Element item = (Element) items.item(0);
        return Optional.of(new KasiRiseSetItem(
                textOf(item, "locdate"), textOf(item, "sunrise"), textOf(item, "sunset"), textOf(item, "civile")));
    }

    // 값 뒤에 공백이 붙어서 온다.
    private static String textOf(Element parent, String tagName) {
        NodeList nodes = parent.getElementsByTagName(tagName);
        if (nodes.getLength() == 0) {
            return null;
        }
        Node node = nodes.item(0);
        return node.getTextContent().strip();
    }

    private static String describe(String reason) {
        return "천문연 " + RISE_SET + " 호출 실패: " + reason;
    }
}
