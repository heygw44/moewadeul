package com.moewadeul.trip.api;

import static com.moewadeul.common.testsupport.TestCsrf.csrf;
import static com.moewadeul.member.domain.MemberBuilder.aMember;
import static org.assertj.core.api.Assertions.assertThat;

import com.moewadeul.common.testsupport.IntegrationTest;
import com.moewadeul.common.testsupport.MutableClock;
import com.moewadeul.common.testsupport.TestSequence;
import com.moewadeul.member.application.verification.EmailVerificationService;
import com.moewadeul.member.domain.Member;
import com.moewadeul.member.infra.MemberJpaRepository;
import com.moewadeul.spot.domain.GeoPoint;
import com.moewadeul.spot.domain.ParkAreaJudgement;
import com.moewadeul.spot.domain.Spot;
import com.moewadeul.spot.infra.SpotJpaRepository;
import jakarta.servlet.http.Cookie;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
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
class SoloTripApiIntegrationTest {

    private static final String VALID_PASSWORD = "Valid-pass1";
    private static final String SESSION_COOKIE = "SESSION";
    private static final String LOCAL_IP = "127.0.0.1";

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
    private JdbcTemplate jdbc;

    @Test
    @DisplayName("[F-23] 인증 회원이 일정을 등록하면 201이고, 내 일정 목록에 장소와 날짜가 보이며, 삭제하면 204이고 목록에서 빠진다")
    void registerListAndDelete() {
        // given
        long spotId = saveSpot();
        Cookie session = verifiedSession(saveMember());

        // when
        MvcTestResult created =
                send(mvc.post().uri("/api/solo-trips"), session, body(spotId, "2026-11-07", "2026-11-09"));
        MvcTestResult list = mvc.get().uri("/api/me/solo-trips").cookie(session).exchange();

        // then
        assertThat(created).hasStatus(HttpStatus.CREATED);
        int tripId = tripIdOf(created);
        assertThat(list).hasStatus(HttpStatus.OK);
        assertThat(list).bodyJson().extractingPath("$").asList().hasSize(1);
        assertThat(list).bodyJson().extractingPath("$[0].soloTripId").isEqualTo(tripId);
        assertThat(list).bodyJson().extractingPath("$[0].spot.spotId").isEqualTo((int) spotId);
        assertThat(list).bodyJson().extractingPath("$[0].spot.name").isEqualTo("능선 끝 평지");
        assertThat(list).bodyJson().extractingPath("$[0].spot.type").isEqualTo("BAKJI");
        assertThat(list).bodyJson().extractingPath("$[0].startDate").isEqualTo("2026-11-07");
        assertThat(list).bodyJson().extractingPath("$[0].endDate").isEqualTo("2026-11-09");

        // when
        MvcTestResult deleted = send(mvc.delete().uri("/api/solo-trips/" + tripId), session, null);
        MvcTestResult afterDelete =
                mvc.get().uri("/api/me/solo-trips").cookie(session).exchange();

        // then
        assertThat(deleted).hasStatus(HttpStatus.NO_CONTENT);
        assertThat(afterDelete).bodyJson().extractingPath("$").asList().isEmpty();
    }

    @Test
    @DisplayName("[F-23][NFR-03] 같은 Idempotency-Key로 일정을 두 번 보내면 일정은 하나만 만들고, 두 번째에도 처음 soloTripId를 응답한다")
    void sameKeyRegistersOneTrip() {
        // given
        long spotId = saveSpot();
        Cookie session = verifiedSession(saveMember());
        String key = UUID.randomUUID().toString();
        String body = body(spotId, "2026-11-07", "2026-11-09");

        // when
        MvcTestResult first = sendWithKey(session, key, body);
        MvcTestResult second = sendWithKey(session, key, body);

        // then
        assertThat(first).hasStatus(HttpStatus.CREATED);
        assertThat(second).hasStatus(HttpStatus.CREATED);
        assertThat(tripIdOf(second)).isEqualTo(tripIdOf(first));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM solo_trip", Integer.class))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("[F-23][ST-01] 로그인하지 않으면 등록·목록이 401이고, 이메일 인증 전 회원은 등록이 403 MEMBER_NOT_VERIFIED이며 목록은 볼 수 있다")
    void authorizationLevels() {
        // given
        long spotId = saveSpot();
        String body = body(spotId, "2026-11-07", "2026-11-09");
        Cookie unverified = login(saveMember());

        // when
        MvcTestResult anonymous = mvc.post()
                .uri("/api/solo-trips")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .exchange();
        MvcTestResult anonymousList = mvc.get().uri("/api/me/solo-trips").exchange();
        MvcTestResult unverifiedWrite = send(mvc.post().uri("/api/solo-trips"), unverified, body);
        MvcTestResult unverifiedList =
                mvc.get().uri("/api/me/solo-trips").cookie(unverified).exchange();

        // then
        assertThat(anonymous).hasStatus(HttpStatus.UNAUTHORIZED);
        assertThat(anonymousList).hasStatus(HttpStatus.UNAUTHORIZED);
        assertThat(unverifiedWrite).hasStatus(HttpStatus.FORBIDDEN);
        assertThat(unverifiedWrite).bodyJson().extractingPath("$.code").isEqualTo("MEMBER_NOT_VERIFIED");
        assertThat(unverifiedList).hasStatus(HttpStatus.OK);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM solo_trip", Integer.class))
                .isZero();
    }

    @Test
    @DisplayName("[F-23][ST-02] 날짜가 범위를 벗어나거나 빠지면 400 SOLO_TRIP_SCHEDULE_INVALID, 장소가 빠지면 400 INVALID_INPUT이다")
    void rejectsInvalidRequest() {
        // given
        long spotId = saveSpot();
        Cookie session = verifiedSession(saveMember());

        // when
        MvcTestResult today =
                send(mvc.post().uri("/api/solo-trips"), session, body(spotId, "2026-10-05", "2026-10-06"));
        MvcTestResult fourNights =
                send(mvc.post().uri("/api/solo-trips"), session, body(spotId, "2026-11-07", "2026-11-11"));
        MvcTestResult noDates = send(mvc.post().uri("/api/solo-trips"), session, "{\"spotId\":%d}".formatted(spotId));
        MvcTestResult noSpot = send(
                mvc.post().uri("/api/solo-trips"),
                session,
                "{\"startDate\":\"2026-11-07\",\"endDate\":\"2026-11-08\"}");

        // then
        assertThat(today).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(today).bodyJson().extractingPath("$.code").isEqualTo("SOLO_TRIP_SCHEDULE_INVALID");
        assertThat(fourNights).bodyJson().extractingPath("$.code").isEqualTo("SOLO_TRIP_SCHEDULE_INVALID");
        assertThat(noDates).bodyJson().extractingPath("$.code").isEqualTo("SOLO_TRIP_SCHEDULE_INVALID");
        assertThat(noSpot).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(noSpot).bodyJson().extractingPath("$.code").isEqualTo("INVALID_INPUT");
    }

    @Test
    @DisplayName("[F-23][ST-03] 내 일정과 밤이 겹치면 409 SOLO_TRIP_OVERLAP이다")
    void overlapIsConflict() {
        // given
        long spotId = saveSpot();
        Cookie session = verifiedSession(saveMember());
        send(mvc.post().uri("/api/solo-trips"), session, body(spotId, "2026-11-07", "2026-11-09"));

        // when
        MvcTestResult overlap =
                send(mvc.post().uri("/api/solo-trips"), session, body(spotId, "2026-11-08", "2026-11-10"));

        // then
        assertThat(overlap).hasStatus(HttpStatus.CONFLICT);
        assertThat(overlap).bodyJson().extractingPath("$.code").isEqualTo("SOLO_TRIP_OVERLAP");
    }

    @Test
    @DisplayName("[F-23][ST-05] 다른 회원의 일정을 지우면 403 ACCESS_DENIED이고, 그 회원의 목록에는 보이지 않는다")
    void otherMembersTripIsForbidden() {
        // given
        long spotId = saveSpot();
        Cookie owner = verifiedSession(saveMember());
        Cookie other = verifiedSession(saveMember());
        int tripId = tripIdOf(send(mvc.post().uri("/api/solo-trips"), owner, body(spotId, "2026-11-07", "2026-11-09")));

        // when
        MvcTestResult delete = send(mvc.delete().uri("/api/solo-trips/" + tripId), other, null);
        MvcTestResult otherList =
                mvc.get().uri("/api/me/solo-trips").cookie(other).exchange();

        // then
        assertThat(delete).hasStatus(HttpStatus.FORBIDDEN);
        assertThat(delete).bodyJson().extractingPath("$.code").isEqualTo("ACCESS_DENIED");
        assertThat(otherList).bodyJson().extractingPath("$").asList().isEmpty();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM solo_trip", Integer.class))
                .isEqualTo(1);
    }

    private static String body(long spotId, String startDate, String endDate) {
        return "{\"spotId\":%d,\"startDate\":\"%s\",\"endDate\":\"%s\"}".formatted(spotId, startDate, endDate);
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

    private MvcTestResult sendWithKey(Cookie session, String key, String body) {
        return mvc.post()
                .uri("/api/solo-trips")
                .with(csrf())
                .cookie(session)
                .header("Idempotency-Key", key)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .exchange();
    }

    private static int tripIdOf(MvcTestResult created) {
        AtomicInteger tripId = new AtomicInteger();
        assertThat(created)
                .bodyJson()
                .extractingPath("$.soloTripId")
                .asNumber()
                .satisfies(number -> tripId.set(number.intValue()));
        return tripId.get();
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
}
