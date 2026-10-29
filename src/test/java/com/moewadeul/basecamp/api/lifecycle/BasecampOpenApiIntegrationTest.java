package com.moewadeul.basecamp.api.lifecycle;

import static com.moewadeul.common.testsupport.TestCsrf.csrf;
import static com.moewadeul.member.domain.MemberBuilder.aMember;
import static org.assertj.core.api.Assertions.assertThat;

import com.moewadeul.common.testsupport.IntegrationTest;
import com.moewadeul.common.testsupport.MutableClock;
import com.moewadeul.common.testsupport.TestSequence;
import com.moewadeul.member.application.verification.EmailVerificationIssuer;
import com.moewadeul.member.domain.Member;
import com.moewadeul.member.infra.MemberJpaRepository;
import com.moewadeul.spot.domain.GeoPoint;
import com.moewadeul.spot.domain.ParkAreaJudgement;
import com.moewadeul.spot.domain.Spot;
import com.moewadeul.spot.infra.SpotJpaRepository;
import jakarta.servlet.http.Cookie;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
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
class BasecampOpenApiIntegrationTest {

    private static final String BASECAMPS = "/api/basecamps";
    private static final String VALID_PASSWORD = "Valid-pass1";
    private static final String SESSION_COOKIE = "SESSION";
    private static final String LOCAL_IP = "127.0.0.1";
    private static final int ADULT_BIRTH_YEAR = 2007;

    @Autowired
    private MockMvcTester mvc;

    @Autowired
    private MemberJpaRepository memberRepository;

    @Autowired
    private SpotJpaRepository spotRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private EmailVerificationIssuer emailVerificationIssuer;

    @Autowired
    private JdbcTemplate jdbc;

    private int identitySequence;

    @Test
    @DisplayName("[F-12][BC-01] 본인확인한 회원이 열면 201이고, 모집 중 베이스캠프와 캠프 리더 멤버 행이 저장된다")
    void openSavesRecruitingBasecampWithLeaderMember() {
        // given
        long spotId = saveBakji(ParkAreaJudgement.outside(MutableClock.DEFAULT_INSTANT));
        Member leader = saveMember();
        Cookie session = identityVerifiedSession(leader, "FEMALE");

        // when
        MvcTestResult result = open(session, spotId, "\"capacity\":4");

        // then
        assertThat(result).hasStatus(HttpStatus.CREATED);
        assertThat(result).bodyJson().extractingPath("$.status").isEqualTo("RECRUITING");
        long basecampId = basecampIdOf(result);
        Map<String, Object> row = jdbc.queryForMap(
                "SELECT leader_id, spot_id, title, capacity, status, min_trust_level, age_group_min, age_group_max,"
                        + " same_gender_only, required_gender FROM basecamp WHERE id = ?",
                basecampId);
        assertThat(((Number) row.get("leader_id")).longValue()).isEqualTo(leader.getId());
        assertThat(((Number) row.get("spot_id")).longValue()).isEqualTo(spotId);
        assertThat(row.get("title")).isEqualTo("북한산 백패킹");
        assertThat(((Number) row.get("capacity")).intValue()).isEqualTo(4);
        assertThat(row.get("status")).isEqualTo("RECRUITING");
        assertThat(row.get("min_trust_level")).isNull();
        assertThat(row.get("same_gender_only")).isIn(false, 0);
        assertThat(row.get("required_gender")).isNull();
        List<Map<String, Object>> members = jdbc.queryForList(
                "SELECT member_id, role, status FROM basecamp_member WHERE basecamp_id = ?", basecampId);
        assertThat(members).hasSize(1);
        assertThat(((Number) members.get(0).get("member_id")).longValue()).isEqualTo(leader.getId());
        assertThat(members.get(0).get("role")).isEqualTo("LEADER");
        assertThat(members.get(0).get("status")).isEqualTo("ACTIVE");
    }

    @Test
    @DisplayName("[F-12][NFR-03] 같은 Idempotency-Key로 베이스캠프를 두 번 열면 하나만 만들고, 두 번째에도 처음 basecampId를 응답한다")
    void sameKeyOpensOneBasecamp() {
        // given
        long spotId = saveBakji(ParkAreaJudgement.outside(MutableClock.DEFAULT_INSTANT));
        Cookie session = identityVerifiedSession(saveMember(), "FEMALE");
        String key = UUID.randomUUID().toString();
        String body = bodyWith(spotId, "2026-10-20", "2026-10-22", "\"capacity\":4");

        // when
        MvcTestResult first = sendWithKey(session, key, body);
        MvcTestResult second = sendWithKey(session, key, body);

        // then
        assertThat(first).hasStatus(HttpStatus.CREATED);
        assertThat(second).hasStatus(HttpStatus.CREATED);
        assertThat(basecampIdOf(second)).isEqualTo(basecampIdOf(first));
        assertThat(countBasecamps()).isEqualTo(1);
    }

    @Test
    @DisplayName("[F-12][BC-05] 합류 조건을 걸고 동성만 받으면 required_gender에 캠프 리더의 본인확인 성별이 저장된다")
    void joinConditionIsSavedWithLeaderGender() {
        // given
        long spotId = saveBakji(ParkAreaJudgement.outside(MutableClock.DEFAULT_INSTANT));
        Cookie session = identityVerifiedSession(saveMember(), "MALE");
        String condition =
                "\"joinCondition\":{\"minTrustLevel\":1,\"ageGroupMin\":20,\"ageGroupMax\":30,\"sameGenderOnly\":true}";

        // when
        MvcTestResult result = open(session, spotId, "\"capacity\":3", condition);

        // then
        assertThat(result).hasStatus(HttpStatus.CREATED);
        Map<String, Object> row = jdbc.queryForMap(
                "SELECT min_trust_level, age_group_min, age_group_max, same_gender_only, required_gender"
                        + " FROM basecamp WHERE id = ?",
                basecampIdOf(result));
        assertThat(((Number) row.get("min_trust_level")).intValue()).isEqualTo(1);
        assertThat(((Number) row.get("age_group_min")).intValue()).isEqualTo(20);
        assertThat(((Number) row.get("age_group_max")).intValue()).isEqualTo(30);
        assertThat(row.get("same_gender_only")).isIn(true, 1);
        assertThat(row.get("required_gender")).isEqualTo("MALE");
    }

    @Test
    @DisplayName("[F-12][BC-01] 본인확인을 하지 않은 회원이 열면 403 TRUST_LEVEL_INSUFFICIENT이고 저장하지 않는다")
    void notIdentityVerifiedMemberIsForbidden() {
        // given
        long spotId = saveBakji(ParkAreaJudgement.outside(MutableClock.DEFAULT_INSTANT));
        Cookie session = verifiedSession(saveMember());

        // when
        MvcTestResult result = open(session, spotId, "\"capacity\":4");

        // then
        assertThat(result).hasStatus(HttpStatus.FORBIDDEN);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("TRUST_LEVEL_INSUFFICIENT");
        assertThat(countBasecamps()).isZero();
    }

    @Test
    @DisplayName("[F-12][BC-04] 공원 경계 경고가 붙은 박지에서는 400 BASECAMP_WARNING_SPOT이고, 경고가 없는 박지와 야영장에서는 열린다")
    void warningBakjiIsRejectedButOthersAreAccepted() {
        // given
        long areaId = insertProtectedArea();
        long warningSpot = saveBakji(ParkAreaJudgement.inside(areaId, MutableClock.DEFAULT_INSTANT));
        long cleanSpot = saveBakji(ParkAreaJudgement.outside(MutableClock.DEFAULT_INSTANT));
        long campsite = saveBakji(ParkAreaJudgement.inside(areaId, MutableClock.DEFAULT_INSTANT));
        jdbc.update("UPDATE spot SET type = 'CAMPSITE' WHERE id = ?", campsite);
        Cookie session = identityVerifiedSession(saveMember(), "FEMALE");

        // when
        MvcTestResult warning = open(session, warningSpot, "\"capacity\":4");
        MvcTestResult clean = open(session, cleanSpot, "\"capacity\":4");
        MvcTestResult camp = open(session, campsite, "\"capacity\":4");

        // then
        assertThat(warning).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(warning).bodyJson().extractingPath("$.code").isEqualTo("BASECAMP_WARNING_SPOT");
        assertThat(clean).hasStatus(HttpStatus.CREATED);
        assertThat(camp).hasStatus(HttpStatus.CREATED);
        assertThat(countBasecamps()).isEqualTo(2);
    }

    @Test
    @DisplayName("[F-12] 없는 장소와 숨긴 장소에서는 404 NOT_FOUND다")
    void missingAndHiddenSpotsAreNotFound() {
        // given
        long hiddenSpot = saveBakji(ParkAreaJudgement.outside(MutableClock.DEFAULT_INSTANT));
        jdbc.update("UPDATE spot SET status = 'HIDDEN' WHERE id = ?", hiddenSpot);
        Cookie session = identityVerifiedSession(saveMember(), "FEMALE");

        // when
        MvcTestResult hidden = open(session, hiddenSpot, "\"capacity\":4");
        MvcTestResult missing = open(session, hiddenSpot + 1000, "\"capacity\":4");

        // then
        assertThat(hidden).hasStatus(HttpStatus.NOT_FOUND);
        assertThat(hidden).bodyJson().extractingPath("$.code").isEqualTo("NOT_FOUND");
        assertThat(missing).hasStatus(HttpStatus.NOT_FOUND);
        assertThat(countBasecamps()).isZero();
    }

    @Test
    @DisplayName("[F-12][BC-01] 모집 중 2개와 마감 1개를 열어 둔 회원이 열면 400 BASECAMP_OPEN_LIMIT이다")
    void recruitingAndClosedCountTowardOpenLimit() {
        // given
        long spotId = saveBakji(ParkAreaJudgement.outside(MutableClock.DEFAULT_INSTANT));
        Member leader = saveMember();
        Cookie session = identityVerifiedSession(leader, "FEMALE");
        insertBasecamp(leader, spotId, "RECRUITING");
        insertBasecamp(leader, spotId, "RECRUITING");
        insertBasecamp(leader, spotId, "CLOSED");

        // when
        MvcTestResult result = open(session, spotId, "\"capacity\":4");

        // then
        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("BASECAMP_OPEN_LIMIT");
        assertThat(countBasecamps()).isEqualTo(3);
    }

    @Test
    @DisplayName("[F-12][BC-01] 확정·완료·취소된 베이스캠프는 개수에 세지 않고, 다른 회원이 연 것도 세지 않는다")
    void finishedAndOthersBasecampsDoNotCount() {
        // given
        long spotId = saveBakji(ParkAreaJudgement.outside(MutableClock.DEFAULT_INSTANT));
        Member leader = saveMember();
        Member other = saveMember();
        Cookie session = identityVerifiedSession(leader, "FEMALE");
        insertBasecamp(leader, spotId, "CONFIRMED");
        insertBasecamp(leader, spotId, "COMPLETED");
        insertBasecamp(leader, spotId, "CANCELED");
        insertBasecamp(other, spotId, "RECRUITING");
        insertBasecamp(other, spotId, "RECRUITING");
        insertBasecamp(other, spotId, "RECRUITING");

        // when
        MvcTestResult result = open(session, spotId, "\"capacity\":4");

        // then
        assertThat(result).hasStatus(HttpStatus.CREATED);
    }

    @Test
    @DisplayName("[F-12][BC-02] 정원이 7명이면 400 BASECAMP_CAPACITY_INVALID이다")
    void capacityOutOfRangeIsRejected() {
        // given
        long spotId = saveBakji(ParkAreaJudgement.outside(MutableClock.DEFAULT_INSTANT));
        Cookie session = identityVerifiedSession(saveMember(), "FEMALE");

        // when
        MvcTestResult result = open(session, spotId, "\"capacity\":7");

        // then
        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("BASECAMP_CAPACITY_INVALID");
    }

    @Test
    @DisplayName("[F-12][BC-03] 출발일이 오늘이거나 4박이면 400 BASECAMP_SCHEDULE_INVALID이다")
    void scheduleOutOfRangeIsRejected() {
        // given
        long spotId = saveBakji(ParkAreaJudgement.outside(MutableClock.DEFAULT_INSTANT));
        Cookie session = identityVerifiedSession(saveMember(), "FEMALE");

        // when
        MvcTestResult today = send(session, bodyWith(spotId, "2026-10-05", "2026-10-06", "\"capacity\":4"));
        MvcTestResult fourNights = send(session, bodyWith(spotId, "2026-10-20", "2026-10-24", "\"capacity\":4"));

        // then
        assertThat(today).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(today).bodyJson().extractingPath("$.code").isEqualTo("BASECAMP_SCHEDULE_INVALID");
        assertThat(fourNights).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(fourNights).bodyJson().extractingPath("$.code").isEqualTo("BASECAMP_SCHEDULE_INVALID");
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 3, -1})
    @DisplayName("[F-12][BC-05] 최소 신뢰 단계가 1·2가 아니면 400 INVALID_INPUT과 허용 값을 알려 준다")
    void invalidMinTrustLevelIsRejected(int minTrustLevel) {
        // given
        long spotId = saveBakji(ParkAreaJudgement.outside(MutableClock.DEFAULT_INSTANT));
        Cookie session = identityVerifiedSession(saveMember(), "FEMALE");

        // when
        MvcTestResult result = open(
                session,
                spotId,
                "\"capacity\":4",
                "\"joinCondition\":{\"minTrustLevel\":" + minTrustLevel + ",\"sameGenderOnly\":false}");

        // then
        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("INVALID_INPUT");
        assertThat(result).bodyJson().extractingPath("$.message").isEqualTo("최소 신뢰 단계는 1 또는 2이어야 합니다.");
        assertThat(countBasecamps()).isZero();
    }

    @Test
    @DisplayName("[F-12][BC-05] 연령대가 십 단위가 아니거나 하한·상한 중 하나만 보내면 400 INVALID_INPUT이다")
    void invalidAgeGroupIsRejected() {
        // given
        long spotId = saveBakji(ParkAreaJudgement.outside(MutableClock.DEFAULT_INSTANT));
        Cookie session = identityVerifiedSession(saveMember(), "FEMALE");

        // when
        MvcTestResult notTens = open(
                session,
                spotId,
                "\"capacity\":4",
                "\"joinCondition\":{\"ageGroupMin\":25,\"ageGroupMax\":30,\"sameGenderOnly\":false}");
        MvcTestResult onlyOne = open(
                session, spotId, "\"capacity\":4", "\"joinCondition\":{\"ageGroupMin\":20,\"sameGenderOnly\":false}");

        // then
        assertThat(notTens).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(notTens).bodyJson().extractingPath("$.code").isEqualTo("INVALID_INPUT");
        assertThat(onlyOne).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(onlyOne).bodyJson().extractingPath("$.code").isEqualTo("INVALID_INPUT");
        assertThat(countBasecamps()).isZero();
    }

    private MvcTestResult open(Cookie session, long spotId, String... extraFields) {
        return send(session, bodyWith(spotId, "2026-10-20", "2026-10-22", extraFields));
    }

    private static String bodyWith(long spotId, String startDate, String endDate, String... extraFields) {
        String fields = String.join(",", extraFields);
        return "{\"spotId\":%d,\"title\":\"북한산 백패킹\",\"description\":\"함께 가요\",\"startDate\":\"%s\",\"endDate\":\"%s\",%s}"
                .formatted(spotId, startDate, endDate, fields);
    }

    private MvcTestResult send(Cookie session, String body) {
        return sendWithKey(session, UUID.randomUUID().toString(), body);
    }

    private MvcTestResult sendWithKey(Cookie session, String key, String body) {
        return mvc.post()
                .uri(BASECAMPS)
                .with(csrf())
                .cookie(session)
                .header("Idempotency-Key", key)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .exchange();
    }

    private static long basecampIdOf(MvcTestResult created) {
        AtomicLong basecampId = new AtomicLong();
        assertThat(created)
                .bodyJson()
                .extractingPath("$.basecampId")
                .asNumber()
                .satisfies(number -> basecampId.set(number.longValue()));
        return basecampId.get();
    }

    private int countBasecamps() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM basecamp", Integer.class);
    }

    private void insertBasecamp(Member leader, long spotId, String status) {
        jdbc.update(
                "INSERT INTO basecamp (leader_id, spot_id, title, description, start_date, end_date, capacity, status,"
                        + " created_at, updated_at) VALUES (?, ?, '기존', '기존', '2026-10-20', '2026-10-22', 4, ?,"
                        + " '2026-10-05 03:00:00', '2026-10-05 03:00:00')",
                leader.getId(),
                spotId,
                status);
    }

    private long saveBakji(ParkAreaJudgement judgement) {
        Spot spot = Spot.bakji("능선 끝 평지", new GeoPoint(37.25, 127.25), judgement, MutableClock.DEFAULT_INSTANT);
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
        String code = emailVerificationIssuer
                .issueFor(member.getId(), LOCAL_IP, TestSequence.next())
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

    private Cookie identityVerifiedSession(Member member, String gender) {
        Cookie session = verifiedSession(member);
        identitySequence++;
        MvcTestResult result = mvc.post()
                .uri("/api/me/identity-verification")
                .with(csrf())
                .cookie(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"birthYear\":%d,\"gender\":\"%s\",\"demoIdentityKey\":\"demo-leader-%d\"}"
                        .formatted(ADULT_BIRTH_YEAR, gender, identitySequence))
                .exchange();
        assertThat(result).hasStatus(HttpStatus.OK);
        return session;
    }

    private long insertProtectedArea() {
        jdbc.update(
                "INSERT INTO protected_area (name, area_type, source, source_date, boundary, created_at, updated_at)"
                        + " VALUES ('북한산', 'NATIONAL_PARK', 'KDPA', '2026-01-01',"
                        + " ST_GeomFromText('MULTIPOLYGON(((127 37, 128 37, 128 38, 127 38, 127 37)))', 4326, 'axis-order=long-lat'),"
                        + " NOW(6), NOW(6))");
        return jdbc.queryForObject("SELECT id FROM protected_area WHERE name = '북한산'", Long.class);
    }
}
