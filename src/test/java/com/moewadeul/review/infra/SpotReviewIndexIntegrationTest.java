package com.moewadeul.review.infra;

import static com.moewadeul.member.domain.MemberBuilder.aMember;
import static org.assertj.core.api.Assertions.assertThat;

import com.moewadeul.common.testsupport.IntegrationTest;
import com.moewadeul.common.testsupport.MutableClock;
import com.moewadeul.member.infra.MemberJpaRepository;
import com.moewadeul.spot.domain.GeoPoint;
import com.moewadeul.spot.domain.ParkAreaJudgement;
import com.moewadeul.spot.domain.Spot;
import com.moewadeul.spot.infra.SpotJpaRepository;
import java.time.LocalDate;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

@IntegrationTest
class SpotReviewIndexIntegrationTest {

    // 장소 상세가 평균 평점을 구할 때 쓰는 회원별 묶음 쿼리와 같은 조건이다.
    private static final String RATING_PER_MEMBER_SQL =
            "SELECT COUNT(*) AS review_count, AVG(r.rating) AS member_average"
                    + " FROM spot_review r WHERE r.spot_id = ? GROUP BY r.member_id";

    private static final int MEMBERS = 30;
    private static final int REVIEWS_PER_MEMBER = 10;
    private static final LocalDate FIRST_VISIT = LocalDate.of(2026, 1, 1);

    @Autowired
    private SpotJpaRepository spotRepository;

    @Autowired
    private MemberJpaRepository memberRepository;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    @DisplayName("[F-05][F-10] 회원별 평균 평점을 낼 때 MySQL은 (spot_id, member_id, rating) 인덱스만 읽고 후기 행을 다시 읽지 않는다")
    void ratingPerMemberReadsOnlySpotMemberRatingIndex() {
        // given: 회원마다 방문일을 달리해 후기를 여러 개 남긴 장소다.
        // 인덱스에 member_id와 rating이 함께 없으면 MySQL이 유니크 인덱스를 훑고 후기마다 행을 다시 읽는다.
        long spotId = saveSpot();
        for (int m = 0; m < MEMBERS; m++) {
            long memberId = memberRepository.saveAndFlush(aMember().build()).getId();
            insertReviews(spotId, memberId);
        }
        jdbc.execute("ANALYZE TABLE spot_review");

        // when
        Map<String, Object> plan = jdbc.queryForMap("EXPLAIN " + RATING_PER_MEMBER_SQL, spotId);

        // then
        assertThat(plan.get("key")).isEqualTo("idx_spot_review_spot_member_rating");
        assertThat((String) plan.get("Extra")).contains("Using index");
    }

    private void insertReviews(long spotId, long memberId) {
        for (int i = 0; i < REVIEWS_PER_MEMBER; i++) {
            jdbc.update(
                    "INSERT INTO spot_review (spot_id, member_id, visited_date, rating, content, created_at, updated_at)"
                            + " VALUES (?, ?, ?, ?, '후기', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6))",
                    spotId,
                    memberId,
                    FIRST_VISIT.plusDays(i),
                    i % 5 + 1);
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
}
