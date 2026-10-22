package com.moewadeul.memory.api;

import static com.moewadeul.common.testsupport.TestCsrf.csrf;
import static com.moewadeul.member.domain.MemberBuilder.aMember;
import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import com.moewadeul.common.testsupport.IntegrationTest;
import com.moewadeul.common.testsupport.MutableClock;
import com.moewadeul.common.testsupport.TestImageStorage;
import com.moewadeul.member.application.verification.EmailVerificationService;
import com.moewadeul.member.domain.Member;
import com.moewadeul.member.infra.MemberJpaRepository;
import com.moewadeul.spot.domain.GeoPoint;
import com.moewadeul.spot.domain.ParkAreaJudgement;
import com.moewadeul.spot.domain.Spot;
import com.moewadeul.spot.infra.SpotJpaRepository;
import jakarta.servlet.http.Cookie;
import java.util.Arrays;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

@IntegrationTest
@AutoConfigureMockMvc
class MemoryApiIntegrationTest {

    private static final String VALID_PASSWORD = "Valid-pass1";
    private static final String SESSION_COOKIE = "SESSION";
    private static final String LOCAL_IP = "127.0.0.1";
    private static final long IMAGE_SIZE = 1024;
    private static final long THUMBNAIL_SIZE = 64;

    // 기본 시각(2026-10-05 한국 정오)의 오늘과 어제다.
    private static final String TODAY = "2026-10-05";
    private static final String YESTERDAY = "2026-10-04";

    @Autowired
    private MockMvcTester mvc;

    @Autowired
    private MemberJpaRepository memberRepository;

    @Autowired
    private SpotJpaRepository spotRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private EmailVerificationService emailVerificationService;

    @Autowired
    private TestImageStorage storage;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    @DisplayName("[F-33][MM-01][MM-02] 인증 회원이 기록을 쓰면 201이고 비공개로 저장되며, 작성자는 상세에서 정확한 좌표와 글을 본다")
    void writeAndReadOwnMemory() {
        // given
        Cookie session = verifiedSession(saveMember());

        // when
        MvcTestResult created = send(mvc.post().uri("/api/memories"), session, """
                {"lat":37.5665,"lng":126.978,"visitedDate":"%s","nights":0,"content":"당일 산행"}""".formatted(TODAY));
        int memoryId = idOf(created, "$.memoryId");
        MvcTestResult detail =
                mvc.get().uri("/api/memories/" + memoryId).cookie(session).exchange();

        // then
        assertThat(created).hasStatus(HttpStatus.CREATED);
        assertThat(detail).hasStatus(HttpStatus.OK);
        assertThat(detail).bodyJson().extractingPath("$.visibility").isEqualTo("PRIVATE");
        assertThat(detail).bodyJson().extractingPath("$.lat").isEqualTo(37.5665);
        assertThat(detail).bodyJson().extractingPath("$.lng").isEqualTo(126.978);
        assertThat(detail).bodyJson().extractingPath("$.nights").isEqualTo(0);
        assertThat(detail).bodyJson().extractingPath("$.content").isEqualTo("당일 산행");
        assertThat(detail).bodyJson().extractingPath("$.images").asList().isEmpty();
        assertThat(detail)
                .bodyJson()
                .extractingPath("$.author.nickname")
                .asString()
                .isNotBlank();
        assertThat(detail).bodyJson().doesNotHavePath("$.spot");
    }

    @Test
    @DisplayName("[F-33][NFR-03] 사진이 붙은 기록을 같은 Idempotency-Key로 두 번 보내면 기록은 하나만 만들고, 두 번째에도 처음 memoryId를 응답한다")
    void sameKeyCreatesOneMemoryEvenWithPhotos() {
        // given
        Cookie session = verifiedSession(saveMember());
        int imageId = uploadedImage(session);
        String key = UUID.randomUUID().toString();
        String body = publicBodyWithImages(YESTERDAY, imageId);

        // when
        MvcTestResult first = sendWithKey(session, key, body);
        MvcTestResult second = sendWithKey(session, key, body);

        // then
        assertThat(first).hasStatus(HttpStatus.CREATED);
        assertThat(second).hasStatus(HttpStatus.CREATED);
        assertThat(idOf(second, "$.memoryId")).isEqualTo(idOf(first, "$.memoryId"));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM memory", Integer.class))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("[F-33][MM-07] 남의 비공개 기록은 회원과 비회원 모두 404이고, 공개로 바꾸면 200이지만 좌표 필드는 없다")
    void privateMemoryIsHiddenAndPublicMemoryHasNoCoordinates() {
        // given
        Cookie owner = verifiedSession(saveMember());
        Cookie other = verifiedSession(saveMember());
        int memoryId = idOf(send(mvc.post().uri("/api/memories"), owner, body(YESTERDAY, 1)), "$.memoryId");

        // when
        MvcTestResult otherPrivate =
                mvc.get().uri("/api/memories/" + memoryId).cookie(other).exchange();
        MvcTestResult anonymousPrivate =
                mvc.get().uri("/api/memories/" + memoryId).exchange();
        MvcTestResult madePublic =
                send(mvc.patch().uri("/api/memories/" + memoryId), owner, "{\"visibility\":\"PUBLIC\"}");
        MvcTestResult otherPublic =
                mvc.get().uri("/api/memories/" + memoryId).cookie(other).exchange();
        MvcTestResult anonymousPublic =
                mvc.get().uri("/api/memories/" + memoryId).exchange();

        // then
        assertThat(otherPrivate).hasStatus(HttpStatus.NOT_FOUND);
        assertThat(otherPrivate).bodyJson().extractingPath("$.code").isEqualTo("NOT_FOUND");
        assertThat(anonymousPrivate).hasStatus(HttpStatus.NOT_FOUND);
        assertThat(madePublic).hasStatus(HttpStatus.OK);
        assertThat(madePublic).bodyJson().extractingPath("$.visibility").isEqualTo("PUBLIC");
        assertThat(madePublic).bodyJson().hasPath("$.lat");
        assertThat(otherPublic).hasStatus(HttpStatus.OK);
        assertThat(otherPublic).bodyJson().doesNotHavePath("$.lat");
        assertThat(otherPublic).bodyJson().doesNotHavePath("$.lng");
        assertThat(otherPublic).bodyJson().extractingPath("$.content").isEqualTo("물가 옆 평지");
        assertThat(anonymousPublic).hasStatus(HttpStatus.OK);
        assertThat(anonymousPublic).bodyJson().doesNotHavePath("$.lat");
    }

    @Test
    @DisplayName("[F-33][MM-06] 남의 기록을 고치거나 지우면 403이고, 없는 기록이면 404이며, 기록은 그대로 남는다")
    void othersCannotReviseOrDelete() {
        // given
        Cookie owner = verifiedSession(saveMember());
        Cookie other = verifiedSession(saveMember());
        int memoryId = idOf(send(mvc.post().uri("/api/memories"), owner, body(YESTERDAY, 1)), "$.memoryId");

        // when
        MvcTestResult revise = send(mvc.patch().uri("/api/memories/" + memoryId), other, "{\"visibility\":\"PUBLIC\"}");
        MvcTestResult delete = send(mvc.delete().uri("/api/memories/" + memoryId), other, null);
        MvcTestResult missing = send(mvc.patch().uri("/api/memories/999999"), owner, "{\"nights\":2}");

        // then
        assertThat(revise).hasStatus(HttpStatus.FORBIDDEN);
        assertThat(revise).bodyJson().extractingPath("$.code").isEqualTo("ACCESS_DENIED");
        assertThat(delete).hasStatus(HttpStatus.FORBIDDEN);
        assertThat(missing).hasStatus(HttpStatus.NOT_FOUND);
        assertThat(jdbc.queryForObject("SELECT visibility FROM memory WHERE id = ?", String.class, memoryId))
                .isEqualTo("PRIVATE");
    }

    @Test
    @DisplayName("[F-33][MM-04] 작성자가 지우면 204이고, 다시 조회하면 404다")
    void ownerDeletesMemory() {
        // given
        Cookie owner = verifiedSession(saveMember());
        int memoryId = idOf(send(mvc.post().uri("/api/memories"), owner, body(YESTERDAY, 1)), "$.memoryId");

        // when
        MvcTestResult deleted = send(mvc.delete().uri("/api/memories/" + memoryId), owner, null);
        MvcTestResult after =
                mvc.get().uri("/api/memories/" + memoryId).cookie(owner).exchange();

        // then
        assertThat(deleted).hasStatus(HttpStatus.NO_CONTENT);
        assertThat(after).hasStatus(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("[F-33][TR-03] 로그인하지 않으면 쓰기와 내 기록이 401이고, 이메일 인증 전 회원은 쓰기·사진 발급이 403이며 내 기록은 볼 수 있다")
    void authorizationLevels() {
        // given
        Cookie unverified = login(saveMember());

        // when
        MvcTestResult anonymous = mvc.post()
                .uri("/api/memories")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(YESTERDAY, 1))
                .exchange();
        MvcTestResult anonymousList = mvc.get().uri("/api/me/memories").exchange();
        MvcTestResult unverifiedWrite = send(mvc.post().uri("/api/memories"), unverified, body(YESTERDAY, 1));
        MvcTestResult unverifiedUpload = send(
                mvc.post().uri("/api/memories/images"),
                unverified,
                "{\"contentType\":\"image/jpeg\",\"sizeBytes\":1024}");
        MvcTestResult unverifiedList =
                mvc.get().uri("/api/me/memories").cookie(unverified).exchange();

        // then
        assertThat(anonymous).hasStatus(HttpStatus.UNAUTHORIZED);
        assertThat(anonymousList).hasStatus(HttpStatus.UNAUTHORIZED);
        assertThat(unverifiedWrite).hasStatus(HttpStatus.FORBIDDEN);
        assertThat(unverifiedWrite).bodyJson().extractingPath("$.code").isEqualTo("MEMBER_NOT_VERIFIED");
        assertThat(unverifiedUpload).hasStatus(HttpStatus.FORBIDDEN);
        assertThat(unverifiedList).hasStatus(HttpStatus.OK);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM memory", Integer.class))
                .isZero();
    }

    @Test
    @DisplayName("[F-33][MM-02] 내일 방문일, 31박, 2,001자 글, 공백뿐인 글, 범위 밖 위도, 모르는 공개 범위는 400 INVALID_INPUT이다")
    void rejectsInvalidInput() {
        // given
        Cookie session = verifiedSession(saveMember());
        String longContent = "가".repeat(2001);

        // when, then
        assertInvalid(session, body("2026-10-06", 1), "visitedDate");
        assertInvalid(session, body(YESTERDAY, 31), "nights");
        assertInvalid(
                session,
                "{\"lat\":37.1,\"lng\":127.1,\"visitedDate\":\"%s\",\"nights\":1,\"content\":\"%s\"}"
                        .formatted(YESTERDAY, longContent),
                "content");
        assertInvalid(
                session,
                "{\"lat\":37.1,\"lng\":127.1,\"visitedDate\":\"%s\",\"nights\":1,\"content\":\"   \"}"
                        .formatted(YESTERDAY),
                "content");
        assertInvalid(
                session, "{\"lat\":91,\"lng\":127.1,\"visitedDate\":\"%s\",\"nights\":1}".formatted(YESTERDAY), "lat");
        assertInvalid(
                session,
                "{\"lat\":37.1,\"lng\":127.1,\"visitedDate\":\"%s\",\"nights\":1,\"visibility\":\"FRIENDS\"}"
                        .formatted(YESTERDAY),
                "visibility");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM memory", Integer.class))
                .isZero();
    }

    @Test
    @DisplayName("[F-33][MM-03] 지도에 보이는 장소를 연결하면 상세에 장소 이름이 나오고, 숨긴 장소는 400 INVALID_INPUT이다")
    void linksOnlyActiveSpot() {
        // given
        Cookie session = verifiedSession(saveMember());
        long active = saveSpot();
        long hidden = saveSpot();
        jdbc.update("UPDATE spot SET status = 'HIDDEN' WHERE id = ?", hidden);

        // when
        MvcTestResult linked = send(mvc.post().uri("/api/memories"), session, bodyWithSpot(active));
        MvcTestResult hiddenLink = send(mvc.post().uri("/api/memories"), session, bodyWithSpot(hidden));
        MvcTestResult detail = mvc.get()
                .uri("/api/memories/" + idOf(linked, "$.memoryId"))
                .cookie(session)
                .exchange();

        // then
        assertThat(linked).hasStatus(HttpStatus.CREATED);
        assertThat(detail).bodyJson().extractingPath("$.spot.spotId").isEqualTo((int) active);
        assertThat(detail).bodyJson().extractingPath("$.spot.name").isEqualTo("능선 끝 평지");
        assertThat(hiddenLink).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(hiddenLink)
                .bodyJson()
                .extractingPath("$.fieldErrors[0].field")
                .isEqualTo("spotId");
    }

    @Test
    @DisplayName("[F-33][MM-04] 수정에서 content를 null로 보내면 글을 지우고, 위도만 보내거나 공개 범위를 null로 보내면 400이다")
    void patchSemantics() {
        // given
        Cookie session = verifiedSession(saveMember());
        int memoryId = idOf(send(mvc.post().uri("/api/memories"), session, body(YESTERDAY, 1)), "$.memoryId");

        // when
        MvcTestResult cleared = send(mvc.patch().uri("/api/memories/" + memoryId), session, "{\"content\":null}");
        MvcTestResult latOnly = send(mvc.patch().uri("/api/memories/" + memoryId), session, "{\"lat\":36.0}");
        MvcTestResult nullVisibility =
                send(mvc.patch().uri("/api/memories/" + memoryId), session, "{\"visibility\":null}");
        MvcTestResult moved = send(
                mvc.patch().uri("/api/memories/" + memoryId), session, "{\"lat\":36.0,\"lng\":128.5,\"nights\":3}");

        // then
        assertThat(cleared).hasStatus(HttpStatus.OK);
        assertThat(cleared).bodyJson().doesNotHavePath("$.content");
        assertThat(latOnly).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(latOnly).bodyJson().extractingPath("$.fieldErrors[0].field").isEqualTo("lng");
        assertThat(nullVisibility).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(moved).hasStatus(HttpStatus.OK);
        assertThat(moved).bodyJson().extractingPath("$.lat").isEqualTo(36.0);
        assertThat(moved).bodyJson().extractingPath("$.lng").isEqualTo(128.5);
        assertThat(moved).bodyJson().extractingPath("$.nights").isEqualTo(3);
        assertThat(moved).bodyJson().extractingPath("$.visitedDate").isEqualTo(YESTERDAY);
    }

    @Test
    @DisplayName("[F-33][MM-05] 사진 URL을 받아 올린 뒤 기록에 붙이면 상세와 내 기록 목록에 순서대로 보인다")
    void attachesUploadedImages() {
        // given
        Cookie session = verifiedSession(saveMember());
        int first = uploadedImage(session);
        int second = uploadedImage(session);

        // when
        MvcTestResult created = send(
                mvc.post().uri("/api/memories"),
                session,
                "{\"lat\":37.1,\"lng\":127.1,\"visitedDate\":\"%s\",\"nights\":1,\"imageIds\":[%d,%d]}"
                        .formatted(YESTERDAY, second, first));
        MvcTestResult detail = mvc.get()
                .uri("/api/memories/" + idOf(created, "$.memoryId"))
                .cookie(session)
                .exchange();
        MvcTestResult list = mvc.get().uri("/api/me/memories").cookie(session).exchange();

        // then
        assertThat(created).hasStatus(HttpStatus.CREATED);
        assertThat(detail).bodyJson().extractingPath("$.images[0].imageId").isEqualTo(second);
        assertThat(detail).bodyJson().extractingPath("$.images[1].imageId").isEqualTo(first);
        assertThat(list).bodyJson().extractingPath("$[0].imageCount").isEqualTo(2);
        assertThat(list)
                .bodyJson()
                .extractingPath("$[0].thumbnailUrl")
                .asString()
                .contains(keyOf(second));
    }

    @Test
    @DisplayName(
            "[F-33][MM-05] 썸네일 크기를 보내면 _thumb.jpg 키의 썸네일 업로드 URL을 더 주고, 보내지 않으면 thumbnailUpload 필드가 없으며, 256KB를 넘으면 400이다")
    void issuesThumbnailUploadOnlyWhenRequested() {
        // given
        Cookie session = verifiedSession(saveMember());

        // when
        MvcTestResult withThumbnail = send(
                mvc.post().uri("/api/memories/images"),
                session,
                "{\"contentType\":\"image/png\",\"sizeBytes\":%d,\"thumbnailSizeBytes\":%d}"
                        .formatted(IMAGE_SIZE, THUMBNAIL_SIZE));
        MvcTestResult plain = send(
                mvc.post().uri("/api/memories/images"),
                session,
                "{\"contentType\":\"image/png\",\"sizeBytes\":%d}".formatted(IMAGE_SIZE));
        MvcTestResult tooLarge = send(
                mvc.post().uri("/api/memories/images"),
                session,
                "{\"contentType\":\"image/png\",\"sizeBytes\":%d,\"thumbnailSizeBytes\":262145}".formatted(IMAGE_SIZE));

        // then
        assertThat(withThumbnail).hasStatus(HttpStatus.CREATED);
        String thumbnailKey = thumbnailKeyOf(idOf(withThumbnail, "$.imageId"));
        assertThat(thumbnailKey).endsWith("_thumb.jpg");
        assertThat(withThumbnail)
                .bodyJson()
                .extractingPath("$.thumbnailUpload.uploadUrl")
                .isEqualTo("https://upload.test.invalid/" + thumbnailKey + "?signed=1");
        assertThat(withThumbnail)
                .bodyJson()
                .extractingPath("$.thumbnailUpload.method")
                .isEqualTo("PUT");
        assertThat(withThumbnail)
                .bodyJson()
                .extractingPath("$.thumbnailUpload.headers.Content-Type")
                .isEqualTo("image/jpeg");
        assertThat(withThumbnail)
                .bodyJson()
                .extractingPath("$.thumbnailUpload.headers.Content-Length")
                .isEqualTo(String.valueOf(THUMBNAIL_SIZE));
        assertThat(plain).hasStatus(HttpStatus.CREATED);
        assertThat(plain).bodyJson().doesNotHavePath("$.thumbnailUpload");
        assertThat(tooLarge).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(tooLarge).bodyJson().extractingPath("$.fieldErrors[0].field").isEqualTo("thumbnailSizeBytes");
    }

    @Test
    @DisplayName("[F-33][MM-05] 썸네일 URL도 받은 사진은 썸네일을 올리지 않으면 붙일 수 없고, 둘 다 올리면 붙인다")
    void attachRequiresUploadedThumbnail() {
        // given
        Cookie session = verifiedSession(saveMember());
        int missingThumbnail = issuedWithThumbnail(session);
        storage.markUploaded(keyOf(missingThumbnail), IMAGE_SIZE);
        int complete = uploadedImageWithThumbnail(session);

        // when
        MvcTestResult rejected = send(
                mvc.post().uri("/api/memories"),
                session,
                "{\"lat\":37.1,\"lng\":127.1,\"visitedDate\":\"%s\",\"nights\":1,\"imageIds\":[%d]}"
                        .formatted(YESTERDAY, missingThumbnail));
        MvcTestResult created = send(
                mvc.post().uri("/api/memories"),
                session,
                "{\"lat\":37.1,\"lng\":127.1,\"visitedDate\":\"%s\",\"nights\":1,\"imageIds\":[%d]}"
                        .formatted(YESTERDAY, complete));

        // then
        assertThat(rejected).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(rejected).bodyJson().extractingPath("$.fieldErrors[0].field").isEqualTo("imageIds");
        assertThat(created).hasStatus(HttpStatus.CREATED);
    }

    @Test
    @DisplayName("[F-33][MM-05][MM-08] 목록과 피드의 thumbnailUrl은 첫 사진에 썸네일이 있으면 썸네일 키를, 없으면 원본 키를 서명하고, 상세는 원본을 준다")
    void listsSignThumbnailKeyWithOriginalFallback() {
        // given
        Cookie session = verifiedSession(saveMember());
        int withThumbnail = uploadedImageWithThumbnail(session);
        int plain = uploadedImage(session);
        int plainFirst = uploadedImage(session);
        int memoryWithThumbnail = idOf(
                send(
                        mvc.post().uri("/api/memories"),
                        session,
                        publicBodyWithImages("2026-10-03", withThumbnail, plain)),
                "$.memoryId");
        send(mvc.post().uri("/api/memories"), session, publicBodyWithImages(YESTERDAY, plainFirst));

        // when
        MvcTestResult mine = mvc.get().uri("/api/me/memories").cookie(session).exchange();
        MvcTestResult feed = mvc.get().uri("/api/memories/feed").exchange();
        MvcTestResult detail = mvc.get()
                .uri("/api/memories/" + memoryWithThumbnail)
                .cookie(session)
                .exchange();

        // then
        String thumbnailUrl = viewUrl(thumbnailKeyOf(withThumbnail));
        String originalUrl = viewUrl(keyOf(plainFirst));
        assertThat(mine).bodyJson().extractingPath("$[0].thumbnailUrl").isEqualTo(originalUrl);
        assertThat(mine).bodyJson().extractingPath("$[1].thumbnailUrl").isEqualTo(thumbnailUrl);
        assertThat(feed).bodyJson().extractingPath("$.content[0].thumbnailUrl").isEqualTo(originalUrl);
        assertThat(feed).bodyJson().extractingPath("$.content[0].imageCount").isEqualTo(1);
        assertThat(feed).bodyJson().extractingPath("$.content[1].memoryId").isEqualTo(memoryWithThumbnail);
        assertThat(feed).bodyJson().extractingPath("$.content[1].thumbnailUrl").isEqualTo(thumbnailUrl);
        assertThat(feed).bodyJson().extractingPath("$.content[1].imageCount").isEqualTo(2);
        assertThat(feed).bodyJson().doesNotHavePath("$.content[1].images");
        assertThat(detail).bodyJson().extractingPath("$.images[0].url").isEqualTo(viewUrl(keyOf(withThumbnail)));
    }

    @Test
    @DisplayName("[F-33][MM-05] 다른 회원이 올린 사진을 붙이면 400 INVALID_INPUT(imageIds)이고 기록은 저장하지 않는다")
    void rejectsOthersImage() {
        // given
        Cookie owner = verifiedSession(saveMember());
        Cookie other = verifiedSession(saveMember());
        int othersImage = uploadedImage(other);

        // when
        MvcTestResult result = send(
                mvc.post().uri("/api/memories"),
                owner,
                "{\"lat\":37.1,\"lng\":127.1,\"visitedDate\":\"%s\",\"nights\":1,\"imageIds\":[%d]}"
                        .formatted(YESTERDAY, othersImage));

        // then
        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("INVALID_INPUT");
        assertThat(result).bodyJson().extractingPath("$.fieldErrors[0].field").isEqualTo("imageIds");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM memory", Integer.class))
                .isZero();
    }

    @Test
    @DisplayName("[F-33] 내 기록 목록은 공개·비공개 기록을 모두 정확한 좌표와 함께 방문일이 늦은 순서로 주고, 남의 기록은 넣지 않는다")
    void listsOwnMemoriesNewestVisitFirst() {
        // given
        Cookie session = verifiedSession(saveMember());
        Cookie other = verifiedSession(saveMember());
        int older = idOf(send(mvc.post().uri("/api/memories"), session, body("2026-09-01", 2)), "$.memoryId");
        int newer = idOf(
                send(
                        mvc.post().uri("/api/memories"),
                        session,
                        "{\"lat\":35.5,\"lng\":129.5,\"visitedDate\":\"%s\",\"nights\":0,\"visibility\":\"PUBLIC\"}"
                                .formatted(YESTERDAY)),
                "$.memoryId");
        send(mvc.post().uri("/api/memories"), other, body(YESTERDAY, 1));

        // when
        MvcTestResult list = mvc.get().uri("/api/me/memories").cookie(session).exchange();

        // then
        assertThat(list).hasStatus(HttpStatus.OK);
        assertThat(list).bodyJson().extractingPath("$").asList().hasSize(2);
        assertThat(list).bodyJson().extractingPath("$[0].memoryId").isEqualTo(newer);
        assertThat(list).bodyJson().extractingPath("$[0].visibility").isEqualTo("PUBLIC");
        assertThat(list).bodyJson().extractingPath("$[0].lat").isEqualTo(35.5);
        assertThat(list).bodyJson().doesNotHavePath("$[0].excerpt");
        assertThat(list).bodyJson().doesNotHavePath("$[0].thumbnailUrl");
        assertThat(list).bodyJson().extractingPath("$[1].memoryId").isEqualTo(older);
        assertThat(list).bodyJson().extractingPath("$[1].visibility").isEqualTo("PRIVATE");
        assertThat(list).bodyJson().extractingPath("$[1].excerpt").isEqualTo("물가 옆 평지");
    }

    private MvcTestResult sendWithKey(Cookie session, String key, String body) {
        return mvc.post()
                .uri("/api/memories")
                .with(csrf())
                .cookie(session)
                .header("Idempotency-Key", key)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .exchange();
    }

    private void assertInvalid(Cookie session, String body, String field) {
        MvcTestResult result = send(mvc.post().uri("/api/memories"), session, body);
        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("INVALID_INPUT");
        assertThat(result).bodyJson().extractingPath("$.fieldErrors[0].field").isEqualTo(field);
    }

    private static String body(String visitedDate, int nights) {
        return "{\"lat\":37.1,\"lng\":127.1,\"visitedDate\":\"%s\",\"nights\":%d,\"content\":\"물가 옆 평지\"}"
                .formatted(visitedDate, nights);
    }

    private static String bodyWithSpot(long spotId) {
        return "{\"lat\":37.25,\"lng\":127.25,\"visitedDate\":\"%s\",\"nights\":1,\"spotId\":%d}"
                .formatted(YESTERDAY, spotId);
    }

    // 업로드 URL을 받고, 회원이 그 URL로 파일을 올린 것처럼 테스트 저장소에 객체를 둔다.
    private int uploadedImage(Cookie session) {
        MvcTestResult issued = send(
                mvc.post().uri("/api/memories/images"),
                session,
                "{\"contentType\":\"image/jpeg\",\"sizeBytes\":%d}".formatted(IMAGE_SIZE));
        assertThat(issued).hasStatus(HttpStatus.CREATED);
        int imageId = idOf(issued, "$.imageId");
        storage.markUploaded(keyOf(imageId), IMAGE_SIZE);
        return imageId;
    }

    // 썸네일과 함께 업로드 URL을 받고, 회원이 두 URL로 파일을 올린 것처럼 테스트 저장소에 객체를 둔다.
    private int uploadedImageWithThumbnail(Cookie session) {
        int imageId = issuedWithThumbnail(session);
        storage.markUploaded(keyOf(imageId), IMAGE_SIZE);
        storage.markUploaded(thumbnailKeyOf(imageId), THUMBNAIL_SIZE);
        return imageId;
    }

    private int issuedWithThumbnail(Cookie session) {
        MvcTestResult issued = send(
                mvc.post().uri("/api/memories/images"),
                session,
                "{\"contentType\":\"image/jpeg\",\"sizeBytes\":%d,\"thumbnailSizeBytes\":%d}"
                        .formatted(IMAGE_SIZE, THUMBNAIL_SIZE));
        assertThat(issued).hasStatus(HttpStatus.CREATED);
        return idOf(issued, "$.imageId");
    }

    private static String publicBodyWithImages(String visitedDate, int... imageIds) {
        String ids = Arrays.stream(imageIds).mapToObj(String::valueOf).collect(Collectors.joining(","));
        return "{\"lat\":37.1,\"lng\":127.1,\"visitedDate\":\"%s\",\"nights\":1,\"visibility\":\"PUBLIC\",\"imageIds\":[%s]}"
                .formatted(visitedDate, ids);
    }

    private static String viewUrl(String key) {
        return "https://view.test.invalid/" + key + "?signed=1";
    }

    private String thumbnailKeyOf(int imageId) {
        return jdbc.queryForObject("SELECT thumbnail_object_key FROM memory_image WHERE id = ?", String.class, imageId);
    }

    private String keyOf(int imageId) {
        return jdbc.queryForObject("SELECT object_key FROM memory_image WHERE id = ?", String.class, imageId);
    }

    private MvcTestResult send(MockMvcTester.MockMvcRequestBuilder builder, Cookie session, String body) {
        MockMvcTester.MockMvcRequestBuilder request = builder.with(csrf())
                .cookie(session)
                .header("Idempotency-Key", UUID.randomUUID().toString());
        if (body != null) {
            request = request.contentType(MediaType.APPLICATION_JSON).content(body);
        }
        return request.exchange();
    }

    private static int idOf(MvcTestResult result, String path) {
        try {
            return JsonPath.read(result.getResponse().getContentAsString(), path);
        } catch (java.io.UnsupportedEncodingException e) {
            throw new IllegalStateException(e);
        }
    }

    private long saveSpot() {
        Spot spot = Spot.bakji(
                "능선 끝 평지",
                new GeoPoint(37.25, 127.25),
                ParkAreaJudgement.outside(MutableClock.DEFAULT_INSTANT),
                MutableClock.DEFAULT_INSTANT);
        return spotRepository.save(spot).getId();
    }

    private Member saveMember() {
        return memberRepository.saveAndFlush(
                aMember().passwordHash(passwordEncoder.encode(VALID_PASSWORD)).build());
    }

    private Cookie login(Member member) {
        MvcTestResult result = mvc.post()
                .uri("/api/auth/login")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(member.getEmail(), VALID_PASSWORD))
                .exchange();
        assertThat(result).hasStatus(HttpStatus.OK);
        Cookie session = result.getResponse().getCookie(SESSION_COOKIE);
        assertThat(session).isNotNull();
        return session;
    }

    private Cookie verifiedSession(Member member) {
        Cookie session = login(member);
        String code = emailVerificationService
                .issueFor(member.getId(), LOCAL_IP)
                .orElseThrow()
                .code();
        MvcTestResult verified = mvc.post()
                .uri("/api/me/email-verification")
                .with(csrf())
                .cookie(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"code\":\"%s\"}".formatted(code))
                .exchange();
        assertThat(verified).hasStatus(HttpStatus.OK);
        return session;
    }
}
