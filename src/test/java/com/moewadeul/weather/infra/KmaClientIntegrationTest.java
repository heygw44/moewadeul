package com.moewadeul.weather.infra;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.serverError;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.awaitility.Awaitility.await;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.github.tomakehurst.wiremock.http.Fault;
import com.github.tomakehurst.wiremock.stubbing.Scenario;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import com.moewadeul.common.external.ExternalApiMetrics;
import com.moewadeul.common.external.ExternalApiProperties;
import com.moewadeul.common.testsupport.IntegrationTest;
import com.moewadeul.common.testsupport.MutableClock;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import tools.jackson.databind.json.JsonMapper;

@IntegrationTest
class KmaClientIntegrationTest {

    private static final String VILAGE_FCST_PATH = "/1360000/VilageFcstInfoService_2.0/getVilageFcst";
    private static final String MID_LAND_FCST_PATH = "/1360000/MidFcstInfoService/getMidLandFcst";
    private static final String MID_TA_PATH = "/1360000/MidFcstInfoService/getMidTa";
    private static final String SERVICE_KEY = "test+key/value==";
    private static final int CONCURRENT_REQUESTS = 100;
    private static final int[] NEAR_TIMEOUT_DELAYS_MILLIS = {
        940, 960, 980, 1000, 940, 960, 980, 1000, 940, 960, 980, 1000
    };
    private static final String ENCODED_SERVICE_KEY = "test%2Bkey%2Fvalue%3D%3D";
    private static final String REQUEST_TIMER = "moewadeul.external.api.requests";
    private static final String RECOVERED = "recovered";
    private static final LocalDateTime BASE = LocalDateTime.of(2026, 10, 4, 5, 0);
    private static final LocalDateTime TM_FC = LocalDateTime.of(2026, 10, 4, 6, 0);

    @Autowired
    private KmaClient client;

    @Autowired
    private WireMockServer wireMock;

    @Autowired
    private MeterRegistry meterRegistry;

    @Autowired
    private KmaProperties kmaProperties;

    @Autowired
    private JsonMapper jsonMapper;

    @Autowired
    private ExternalApiMetrics metrics;

    @Autowired
    private MutableClock clock;

    @Test
    @DisplayName("[F-09] 단기예보 응답의 항목을 원천 값 그대로 읽는다")
    void readsShortTermItems() {
        // given
        stub(VILAGE_FCST_PATH, okJson(readFixture("vilage-fcst.json")));

        // when
        List<KmaShortTermItem> items = client.getVilageFcst(BASE, 60, 127);

        // then
        assertThat(items).hasSize(907);
        assertThat(items.getFirst()).isEqualTo(new KmaShortTermItem("TMP", "20261004", "0600", "15"));
    }

    @Test
    @DisplayName("[F-09] 단기예보 요청은 발표 날짜·시각, 격자, 페이지 크기를 쿼리로 보낸다")
    void sendsShortTermQuery() {
        // given
        stub(VILAGE_FCST_PATH, okJson(readFixture("vilage-fcst.json")));

        // when
        client.getVilageFcst(BASE, 60, 127);

        // then
        wireMock.verify(getRequestedFor(urlPathEqualTo(VILAGE_FCST_PATH))
                .withQueryParam("base_date", equalTo("20261004"))
                .withQueryParam("base_time", equalTo("0500"))
                .withQueryParam("nx", equalTo("60"))
                .withQueryParam("ny", equalTo("127"))
                .withQueryParam("pageNo", equalTo("1"))
                .withQueryParam("numOfRows", equalTo("1500"))
                .withQueryParam("dataType", equalTo("JSON")));
    }

    @Test
    @DisplayName("[F-09] 중기 육상예보와 기온예보 응답의 필드를 원천 값 그대로 읽는다")
    void readsMidTermItems() {
        // given
        stub(MID_LAND_FCST_PATH, okJson(readFixture("mid-land-fcst.json")));
        stub(MID_TA_PATH, okJson(readFixture("mid-ta.json")));

        // when
        Optional<KmaMidTermItem> land = client.getMidLandFcst("11B00000", TM_FC);
        Optional<KmaMidTermItem> ta = client.getMidTa("11B10101", TM_FC);

        // then
        assertThat(land).isPresent();
        assertThat(land.get().get("regId")).isEqualTo("11B00000");
        assertThat(land.get().get("wf4Am")).isEqualTo("맑음");
        assertThat(land.get().get("rnSt8")).isEqualTo("20");
        assertThat(ta).isPresent();
        assertThat(ta.get().get("taMin4")).isEqualTo("10");
        assertThat(ta.get().get("taMax10")).isEqualTo("21");
        wireMock.verify(getRequestedFor(urlPathEqualTo(MID_LAND_FCST_PATH))
                .withQueryParam("regId", equalTo("11B00000"))
                .withQueryParam("tmFc", equalTo("202610040600"))
                .withQueryParam("dataType", equalTo("JSON")));
        wireMock.verify(getRequestedFor(urlPathEqualTo(MID_TA_PATH)).withQueryParam("regId", equalTo("11B10101")));
    }

    @Test
    @DisplayName("[F-09] 중기예보 응답에 항목이 없으면 빈 값을 돌려준다")
    void returnsEmptyWhenMidTermItemsAreMissing() {
        // given
        stub(MID_TA_PATH, okJson("""
                {"response": {"header": {"resultCode": "00", "resultMsg": "NORMAL_SERVICE"},
                  "body": {"dataType": "JSON", "items": "", "pageNo": 1, "numOfRows": 10, "totalCount": 0}}}
                """));

        // when
        Optional<KmaMidTermItem> ta = client.getMidTa("11B10101", TM_FC);

        // then
        assertThat(ta).isEmpty();
    }

    @Test
    @DisplayName("[F-09][NFR-06] 클라이언트는 인증키의 +, /, = 를 퍼센트 인코딩해서 보낸다")
    void sendsPercentEncodedServiceKey() {
        // given
        stub(MID_TA_PATH, okJson(readFixture("mid-ta.json")));

        // when
        client.getMidTa("11B10101", TM_FC);

        // then
        String rawServiceKey = rawQueryValue(onlyRequest(MID_TA_PATH), "serviceKey");
        assertThat(rawServiceKey).isEqualTo(ENCODED_SERVICE_KEY);
        assertThat(URLDecoder.decode(rawServiceKey, StandardCharsets.UTF_8)).isEqualTo(SERVICE_KEY);
    }

    @Test
    @DisplayName("[F-09][NFR-05] 17시 발표처럼 단기예보가 1,000건을 넘어도 한 번의 요청으로 모두 받는다")
    void readsShortTermResponseOverOneThousandItems() {
        // given
        // 기상청은 numOfRows만큼만 항목을 담고 totalCount에는 전체 개수를 준다. 2026-10-10 17시 발표는 1,052건이었다.
        int totalCount = 1052;
        wireMock.stubFor(get(urlPathEqualTo(VILAGE_FCST_PATH))
                .atPriority(1)
                .withQueryParam("numOfRows", equalTo("1000"))
                .willReturn(okJson(shortTermResponse(1000, totalCount))));
        wireMock.stubFor(get(urlPathEqualTo(VILAGE_FCST_PATH))
                .atPriority(2)
                .willReturn(okJson(shortTermResponse(totalCount, totalCount))));

        // when
        List<KmaShortTermItem> items = client.getVilageFcst(LocalDateTime.of(2026, 10, 10, 17, 0), 60, 127);

        // then
        assertThat(items).hasSize(totalCount);
        assertThat(requestCount(VILAGE_FCST_PATH)).isEqualTo(1);
    }

    @Test
    @DisplayName("[F-09] 단기예보 전체 개수가 받은 항목 수보다 크면 일부만 받은 응답이라 실패로 본다")
    void throwsWhenShortTermResponseIsIncomplete() {
        // given
        stub(VILAGE_FCST_PATH, okJson("""
                {"response": {"header": {"resultCode": "00", "resultMsg": "NORMAL_SERVICE"},
                  "body": {"dataType": "JSON", "items": {"item": [
                    {"category": "TMP", "fcstDate": "20261004", "fcstTime": "0600", "fcstValue": "15"}]},
                  "pageNo": 1, "numOfRows": 1500, "totalCount": 2000}}}
                """));

        // when
        KmaApiException thrown = catchThrowableOfType(KmaApiException.class, () -> client.getVilageFcst(BASE, 60, 127));

        // then
        assertThat(thrown).hasMessageContaining("totalCount=2000");
        assertThat(requestCount(VILAGE_FCST_PATH)).isEqualTo(1);
    }

    @Test
    @DisplayName("[F-09][NFR-06] 원천이 성공이 아닌 resultCode를 주면 다시 보내지 않고 오류 코드를 담은 예외를 던진다")
    void throwsWithoutRetryWhenResultCodeIsNotSuccess() {
        // given
        stub(MID_TA_PATH, okJson("""
                {"response": {"header": {"resultCode": "10", "resultMsg": "INVALID_REQUEST_PARAMETER_ERROR"}}}
                """));

        // when
        KmaApiException thrown = catchThrowableOfType(KmaApiException.class, () -> client.getMidTa("11B10101", TM_FC));

        // then
        assertThat(thrown)
                .hasMessageContaining("resultCode=10")
                .hasMessageContaining("INVALID_REQUEST_PARAMETER_ERROR");
        assertThat(requestCount(MID_TA_PATH)).isEqualTo(1);
    }

    @Test
    @DisplayName("[F-09][NFR-06] 응답 JSON이 깨졌으면 다시 보내지 않고 예외를 던진다")
    void throwsWithoutRetryWhenJsonIsBroken() {
        // given
        stub(MID_TA_PATH, okJson("{\"response\": {"));

        // when
        KmaApiException thrown = catchThrowableOfType(KmaApiException.class, () -> client.getMidTa("11B10101", TM_FC));

        // then
        assertThat(thrown).hasMessageContaining("JSON");
        assertThat(requestCount(MID_TA_PATH)).isEqualTo(1);
    }

    @Test
    @DisplayName("[F-09][NFR-06] 인증 오류 XML을 받으면 다시 보내지 않고 인증 오류 내용을 담은 예외를 던진다")
    void throwsWithoutRetryWhenAuthErrorXmlIsReturned() {
        // given
        stub(
                MID_TA_PATH,
                aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "text/xml;charset=UTF-8")
                        .withBody("""
                        <OpenAPI_ServiceResponse><cmmMsgHeader><errMsg>SERVICE ERROR</errMsg>
                        <returnAuthMsg>SERVICE_KEY_IS_NOT_REGISTERED_ERROR</returnAuthMsg>
                        <returnReasonCode>30</returnReasonCode></cmmMsgHeader></OpenAPI_ServiceResponse>
                        """));

        // when
        KmaApiException thrown = catchThrowableOfType(KmaApiException.class, () -> client.getMidTa("11B10101", TM_FC));

        // then
        assertThat(thrown).hasMessageContaining("SERVICE_KEY_IS_NOT_REGISTERED_ERROR");
        assertNoServiceKey(thrown);
        assertThat(requestCount(MID_TA_PATH)).isEqualTo(1);
    }

    @Test
    @DisplayName("[F-09][NFR-06] 첫 요청이 500이면 한 번 다시 보내고, 두 요청을 모두 지표에 남긴다")
    void retriesOnceAfterServerErrorAndRecordsBothAttempts() {
        // given
        wireMock.stubFor(get(urlPathEqualTo(MID_TA_PATH))
                .inScenario("server error once")
                .whenScenarioStateIs(Scenario.STARTED)
                .willReturn(serverError())
                .willSetStateTo(RECOVERED));
        wireMock.stubFor(get(urlPathEqualTo(MID_TA_PATH))
                .inScenario("server error once")
                .whenScenarioStateIs(RECOVERED)
                .willReturn(okJson(readFixture("mid-ta.json"))));
        long successBefore = timerCount("getMidTa", "success");
        long failureBefore = timerCount("getMidTa", "failure");

        // when
        Optional<KmaMidTermItem> ta = client.getMidTa("11B10101", TM_FC);

        // then
        assertThat(ta).isPresent();
        assertThat(requestCount(MID_TA_PATH)).isEqualTo(2);
        assertThat(timerCount("getMidTa", "success") - successBefore).isEqualTo(1);
        assertThat(timerCount("getMidTa", "failure") - failureBefore).isEqualTo(1);
    }

    @Test
    @DisplayName("[F-09][NFR-06] 500이 두 번 이어지면 두 번째 요청 뒤에 인증키 없는 예외를 던진다")
    void throwsAfterSecondServerError() {
        // given
        stub(VILAGE_FCST_PATH, serverError());
        long failureBefore = timerCount("getVilageFcst", "failure");

        // when
        KmaApiException thrown = catchThrowableOfType(KmaApiException.class, () -> client.getVilageFcst(BASE, 60, 127));

        // then
        assertThat(thrown).hasMessageContaining("HTTP 500");
        assertNoServiceKey(thrown);
        assertThat(requestCount(VILAGE_FCST_PATH)).isEqualTo(2);
        assertThat(timerCount("getVilageFcst", "failure") - failureBefore).isEqualTo(2);
    }

    @Test
    @DisplayName("[F-09][NFR-05] 응답이 응답 대기 시간보다 늦으면 한 번 다시 보낸 뒤 인증키 없는 예외를 던진다")
    void throwsAfterSecondReadTimeout() {
        // given: 테스트 프로필의 응답 대기 시간은 1초다.
        stub(MID_TA_PATH, okJson(readFixture("mid-ta.json")).withFixedDelay(2_000));
        long failureBefore = timerCount("getMidTa", "failure");

        // when
        KmaApiException thrown = catchThrowableOfType(KmaApiException.class, () -> client.getMidTa("11B10101", TM_FC));

        // then
        assertThat(thrown).hasMessageContaining("I/O 오류");
        assertNoServiceKey(thrown);
        assertThat(timerCount("getMidTa", "failure") - failureBefore).isEqualTo(2);
        // WireMock이 지연 응답을 다 보낸 뒤에야 요청을 기록한다. 그래서 테스트는 기록이 두 건이 될 때까지 기다린다.
        await().atMost(Duration.ofSeconds(5))
                .untilAsserted(() -> assertThat(requestCount(MID_TA_PATH)).isEqualTo(2));
    }

    @Test
    @DisplayName("[F-09][NFR-05] 압축된 응답을 읽는 도중 응답 대기 시간이 지나도, 모든 요청이 정상 응답이나 다시 보낼 수 있는 예외로 끝난다")
    void everyRequestEndsWithRetryableFailureWhenReadTimeoutHitsDuringCompressedBody() throws Exception {
        // given: 테스트 프로필의 응답 대기 시간은 1초다. WireMock은 gzip을 받겠다는 요청에만 본문을 압축해서 보낸다.
        // 응답이 대기 시간 경계 근처에 끝나게 지연을 두면, 압축을 받는 클라이언트에서는 일부 요청의 본문이 압축을 푸는 도중에 닫힌다.
        // 그 경쟁은 드물어서 동시 요청 100건을 지연을 바꿔 가며 여러 번 보낸다. 압축을 받던 때는 이 조건 1,200건 중 약 10건이 예외로 끝났다.
        // 이 경쟁을 보려면 요청 100건이 동시에 나가야 하므로, 동시 호출 수 한도를 넘지 않는 클라이언트를 따로 만들어 쓴다.
        KmaClient unlimited = new KmaClient(
                kmaProperties,
                new ExternalApiProperties(CONCURRENT_REQUESTS, Duration.ZERO),
                jsonMapper,
                metrics,
                clock);
        List<String> unexpected = new CopyOnWriteArrayList<>();
        ExecutorService executor = Executors.newFixedThreadPool(CONCURRENT_REQUESTS);

        // when
        for (int delayMillis : NEAR_TIMEOUT_DELAYS_MILLIS) {
            wireMock.resetAll();
            stub(VILAGE_FCST_PATH, okJson(readFixture("vilage-fcst.json")).withFixedDelay(delayMillis));
            List<Future<?>> futures = new ArrayList<>();
            for (int i = 0; i < CONCURRENT_REQUESTS; i++) {
                futures.add(executor.submit(() -> callRecordingUnexpected(unlimited, unexpected)));
            }
            for (Future<?> future : futures) {
                future.get(30, TimeUnit.SECONDS);
            }
        }
        executor.shutdown();
        assertThat(executor.awaitTermination(30, TimeUnit.SECONDS)).isTrue();

        // then
        assertThat(unexpected).isEmpty();
    }

    private void callRecordingUnexpected(KmaClient target, List<String> unexpected) {
        try {
            target.getVilageFcst(BASE, 60, 127);
        } catch (KmaApiException e) {
            if (!e.isRetryable()) {
                unexpected.add("다시 보낼 수 없는 실패: " + e.getMessage());
            }
        } catch (RuntimeException e) {
            unexpected.add(e.getClass().getName() + ": " + e.getMessage());
        }
    }

    private void stub(String path, ResponseDefinitionBuilder response) {
        wireMock.stubFor(get(urlPathEqualTo(path)).willReturn(response));
    }

    private int requestCount(String path) {
        return wireMock.findAll(getRequestedFor(urlPathEqualTo(path))).size();
    }

    private LoggedRequest onlyRequest(String path) {
        var requests = wireMock.findAll(getRequestedFor(urlPathEqualTo(path)));
        assertThat(requests).hasSize(1);
        return requests.getFirst();
    }

    private long timerCount(String operation, String outcome) {
        Timer timer = meterRegistry
                .find(REQUEST_TIMER)
                .tags("api", "kma", "operation", operation, "outcome", outcome)
                .timer();
        return timer == null ? 0 : timer.count();
    }

    private static String rawQueryValue(LoggedRequest request, String name) {
        String rawQuery = URI.create(request.getUrl()).getRawQuery();
        return Arrays.stream(rawQuery.split("&"))
                .filter(pair -> pair.startsWith(name + "="))
                .map(pair -> pair.substring(name.length() + 1))
                .findFirst()
                .orElseThrow(() -> new AssertionError(name + " 쿼리가 없다"));
    }

    @Test
    @DisplayName("[F-09][NFR-07] 서버가 연결을 끊으면 스택 트레이스 전체에 인증키가 없는 예외를 던진다")
    void connectionResetLeavesNoServiceKeyInStackTrace() {
        // given
        stub(MID_TA_PATH, aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER));

        // when
        KmaApiException thrown = catchThrowableOfType(KmaApiException.class, () -> client.getMidTa("11B10101", TM_FC));

        // then
        assertThat(thrown).hasMessageContaining("I/O 오류");
        assertNoServiceKey(thrown);
    }

    @Test
    @DisplayName("[NFR-05] 기상청이 느릴 때 동시 호출이 한도(10)를 넘으면, 넘는 호출은 기다리지 않고 기상청을 부르지 않은 채 다시 보낼 수 없는 예외로 끝난다")
    void rejectsCallsBeyondConcurrentLimitWithoutCallingKma() throws Exception {
        // given: 테스트 프로필의 응답 대기 시간은 1초다. 응답이 0.5초 걸려서 먼저 들어간 10건이 그동안 자리를 쥔다. 느린 CI 러너에서도 대기 시간을 넘지 않게 여유를 둔다.
        int threads = 30;
        stub(MID_TA_PATH, okJson(readFixture("mid-ta.json")).withFixedDelay(500));
        long successBefore = timerCount("getMidTa", "success");
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<String>> futures = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            futures.add(executor.submit(() -> {
                ready.countDown();
                start.await();
                try {
                    client.getMidTa("11B10101", TM_FC);
                    return "OK";
                } catch (KmaApiException e) {
                    return e.isRetryable() ? "RETRYABLE" : "REJECTED";
                }
            }));
        }

        // when
        ready.await();
        start.countDown();
        List<String> outcomes = new ArrayList<>();
        for (Future<String> future : futures) {
            outcomes.add(future.get(30, TimeUnit.SECONDS));
        }
        executor.shutdown();

        // then
        assertThat(outcomes).containsOnly("OK", "REJECTED");
        assertThat(outcomes.stream().filter("OK"::equals).count()).isBetween(1L, 10L);
        assertThat(timerCount("getMidTa", "success") - successBefore).isBetween(1L, 10L);
    }

    // 키가 예외에 남으면 로그와 오류 추적 도구로 비밀값이 새어 나간다. 서버는 이 예외를 스택 트레이스째 로그에 남기므로,
    // 메시지만이 아니라 로그에 찍히는 스택 트레이스 전체(원인 예외와 억제된 예외 포함)에 키가 없는지 확인한다.
    private static void assertNoServiceKey(Throwable thrown) {
        StringWriter stackTrace = new StringWriter();
        thrown.printStackTrace(new PrintWriter(stackTrace));
        assertThat(stackTrace.toString()).doesNotContain(SERVICE_KEY, ENCODED_SERVICE_KEY);
    }

    private static String shortTermResponse(int itemCount, int totalCount) {
        String items = IntStream.range(0, itemCount).mapToObj(i -> """
                        {"category": "TMP", "fcstDate": "20261010", "fcstTime": "1800", "fcstValue": "15"}""").collect(Collectors.joining(","));
        return """
                {"response": {"header": {"resultCode": "00", "resultMsg": "NORMAL_SERVICE"},
                  "body": {"dataType": "JSON", "items": {"item": [%s]},
                  "pageNo": 1, "numOfRows": %d, "totalCount": %d}}}
                """.formatted(items, itemCount, totalCount);
    }

    @Test
    @DisplayName("[NFR-05][NFR-06] 기상청이 재시도까지 실패하면 쉬는 시간 동안 기상청을 부르지 않고, 쉬는 시간이 지나면 한 호출만 시험 삼아 보낸다")
    void restsAfterOutageAndProbesWithOneCall() {
        // given: 기상청이 계속 500을 준다.
        KmaClient resting = clientWithCooldown();
        stub(VILAGE_FCST_PATH, serverError());
        double skippedBefore = skippedCount();

        // when: 첫 호출은 재시도까지 두 번 보낸 뒤 실패한다. 이어지는 호출은 작업과 격자가 달라도 쉬는 중이라 기상청에 보내지 않는다.
        KmaApiException first = catchThrowableOfType(KmaApiException.class, () -> resting.getVilageFcst(BASE, 60, 127));
        KmaApiException otherGrid =
                catchThrowableOfType(KmaApiException.class, () -> resting.getVilageFcst(BASE, 61, 127));
        KmaApiException otherOperation =
                catchThrowableOfType(KmaApiException.class, () -> resting.getMidTa("11B10101", TM_FC));

        // then
        assertThat(first.isRetryable()).isTrue();
        assertThat(otherGrid.isRetryable()).isFalse();
        assertThat(otherGrid).hasMessageContaining("쉬는 중");
        assertThat(otherOperation).hasMessageContaining("쉬는 중");
        assertThat(requestCount(VILAGE_FCST_PATH)).isEqualTo(2);
        assertThat(requestCount(MID_TA_PATH)).isZero();
        assertThat(skippedCount() - skippedBefore).isEqualTo(2);

        // when: 쉬는 시간이 지나면 한 호출이 재시도까지 두 번 보내고, 또 실패하면 다시 쉰다.
        clock.advance(Duration.ofSeconds(30));
        catchThrowableOfType(KmaApiException.class, () -> resting.getVilageFcst(BASE, 60, 127));
        KmaApiException afterProbe =
                catchThrowableOfType(KmaApiException.class, () -> resting.getVilageFcst(BASE, 60, 127));

        // then
        assertThat(afterProbe).hasMessageContaining("쉬는 중");
        assertThat(requestCount(VILAGE_FCST_PATH)).isEqualTo(4);
    }

    @Test
    @DisplayName("[NFR-05] 쉬는 시간이 지난 뒤 시험 호출이 성공하면 바로 정상으로 돌아와 다음 호출도 기상청에 보낸다")
    void recoversWhenProbeSucceeds() {
        // given: 기상청이 500을 줘서 쉬는 중이다.
        KmaClient resting = clientWithCooldown();
        stub(MID_TA_PATH, serverError());
        catchThrowableOfType(KmaApiException.class, () -> resting.getMidTa("11B10101", TM_FC));
        stub(MID_TA_PATH, okJson(readFixture("mid-ta.json")));
        clock.advance(Duration.ofSeconds(30));

        // when
        Optional<KmaMidTermItem> probe = resting.getMidTa("11B10101", TM_FC);
        Optional<KmaMidTermItem> next = resting.getMidTa("11B10101", TM_FC);

        // then
        assertThat(probe).isPresent();
        assertThat(next).isPresent();
        assertThat(requestCount(MID_TA_PATH)).isEqualTo(4);
    }

    @Test
    @DisplayName("[NFR-05] 기상청이 오류 코드로 답했다면 기상청이 살아 있으므로 쉬지 않는다")
    void doesNotRestWhenKmaAnswersWithErrorCode() {
        // given
        KmaClient resting = clientWithCooldown();
        stub(MID_TA_PATH, okJson("""
                {"response": {"header": {"resultCode": "10", "resultMsg": "INVALID_REQUEST_PARAMETER_ERROR"}}}
                """));

        // when
        catchThrowableOfType(KmaApiException.class, () -> resting.getMidTa("11B10101", TM_FC));
        KmaApiException second = catchThrowableOfType(KmaApiException.class, () -> resting.getMidTa("11B10101", TM_FC));

        // then
        assertThat(second).hasMessageContaining("resultCode=10");
        assertThat(requestCount(MID_TA_PATH)).isEqualTo(2);
    }

    // 테스트 프로필은 다른 테스트가 서로의 실패에 영향받지 않게 쉬는 시간을 0으로 둔다. 쉬는 동작을 보는 테스트는 30초를 쉬는 클라이언트를 따로 만든다.
    private KmaClient clientWithCooldown() {
        return new KmaClient(
                kmaProperties, new ExternalApiProperties(10, Duration.ofSeconds(30)), jsonMapper, metrics, clock);
    }

    private double skippedCount() {
        Counter counter = meterRegistry
                .find("moewadeul.external.api.skipped")
                .tag("api", "kma")
                .counter();
        return counter == null ? 0 : counter.count();
    }

    private static String readFixture(String fileName) {
        try {
            return new ClassPathResource("fixtures/weather/kma/" + fileName).getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
