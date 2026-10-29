package com.moewadeul.trust.infra.companion;

import static org.assertj.core.api.Assertions.assertThat;

import com.moewadeul.common.testsupport.IntegrationTest;
import com.moewadeul.common.testsupport.MutableClock;
import com.moewadeul.common.testsupport.TestSequence;
import com.moewadeul.member.infra.MemberJpaRepository;
import com.moewadeul.trust.application.CompanionReviewFixture;
import com.moewadeul.trust.domain.CompanionReviewPolicy;
import com.moewadeul.trust.domain.companion.CompanionReviewTag;
import com.moewadeul.trust.infra.profile.TagCountRow;
import com.moewadeul.trust.infra.profile.TrustRecordMapper;
import com.moewadeul.trust.infra.profile.TrustRecordRow;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

@IntegrationTest
class CompanionReviewRevealSqlParityIntegrationTest {

    private static final Instant NOW = MutableClock.DEFAULT_INSTANT;
    private static final Instant REVEAL_CUTOFF = CompanionReviewPolicy.revealCutoff(NOW);
    private static final List<Duration> COMPLETED_OFFSETS =
            List.of(Duration.ofSeconds(-1), Duration.ZERO, Duration.ofSeconds(1));
    private static final int PAGE_SIZE = 100;

    @Autowired
    private CompanionReviewMapper reviewMapper;

    @Autowired
    private TrustRecordMapper trustRecordMapper;

    @Autowired
    private MemberJpaRepository memberRepository;

    @Autowired
    private JdbcTemplate jdbc;

    private CompanionReviewFixture fixture;

    @BeforeEach
    void setUp() {
        fixture = new CompanionReviewFixture(jdbc, memberRepository);
    }

    @Test
    @DisplayName("[RV-03] 공개 후기 조회·신뢰 기록 집계·태그 집계는 모두 CompanionReviewPolicy.isRevealed가 공개라고 보는 후기만 센다")
    void everyRevealQueryAgreesWithPolicy() {
        // given
        long revieweeId = fixture.saveVerifiedMember(TestSequence.nickname());
        List<ReviewCase> cases = insertEveryRevealCase(revieweeId);
        List<ReviewCase> revealed = cases.stream().filter(ReviewCase::revealed).toList();

        // when
        Set<String> publicComments =
                reviewMapper.selectPublicByReviewee(revieweeId, REVEAL_CUTOFF, 0, PAGE_SIZE).stream()
                        .map(CompanionReviewPublicRow::comment)
                        .collect(Collectors.toSet());
        TrustRecordRow record =
                trustRecordMapper.selectCompanionRecord(revieweeId, REVEAL_CUTOFF, NOW.minus(Duration.ofDays(90)));
        Map<CompanionReviewTag, Long> tagCounts =
                trustRecordMapper.selectRevealedTagCounts(revieweeId, REVEAL_CUTOFF).stream()
                        .collect(Collectors.toMap(TagCountRow::tag, TagCountRow::count));

        // then
        assertThat(revealed).isNotEmpty().hasSizeLessThan(cases.size());
        assertThat(publicComments)
                .isEqualTo(revealed.stream().map(ReviewCase::comment).collect(Collectors.toSet()));
        assertThat(record.receivedReviews()).isEqualTo(revealed.size());
        assertThat(record.rejoinWanted())
                .isEqualTo(revealed.stream().filter(ReviewCase::rejoinWanted).count());
        assertThat(revealed.stream().filter(ReviewCase::rejoinWanted)).isNotEmpty();
        assertThat(tagCounts).isEqualTo(expectedTagCounts(revealed));
    }

    @Test
    @DisplayName("[RV-03] 받은 후기 목록의 reverse_written은 맞후기를 쓴 경우에만 true다")
    void receivedReviewsReportReverseWritten() {
        // given
        long revieweeId = fixture.saveVerifiedMember(TestSequence.nickname());
        List<ReviewCase> cases = insertEveryRevealCase(revieweeId);

        // when
        Map<Long, Boolean> reverseWrittenByReview = reviewMapper.selectReceived(revieweeId, 0, PAGE_SIZE).stream()
                .collect(Collectors.toMap(
                        CompanionReviewReceivedRow::reviewId, CompanionReviewReceivedRow::reverseWritten));

        // then
        assertThat(reverseWrittenByReview).hasSize(cases.size());
        assertThat(cases)
                .allSatisfy(reviewCase -> assertThat(reverseWrittenByReview.get(reviewCase.reviewId()))
                        .isEqualTo(reviewCase.reverseWritten()));
    }

    private List<ReviewCase> insertEveryRevealCase(long revieweeId) {
        List<ReviewCase> cases = new ArrayList<>();
        CompanionReviewTag[] tags = CompanionReviewTag.values();
        int index = 0;
        for (Duration offset : COMPLETED_OFFSETS) {
            for (boolean reverseWritten : List.of(false, true)) {
                Instant completedAt = REVEAL_CUTOFF.plus(offset);
                long basecampId = fixture.saveBasecamp("COMPLETED", completedAt);
                long reviewerId = fixture.saveVerifiedMember(TestSequence.nickname());
                boolean rejoinWanted = index % 2 == 0;
                long reviewId = fixture.insertReview(basecampId, reviewerId, revieweeId, rejoinWanted, NOW);
                List<CompanionReviewTag> reviewTags =
                        List.of(tags[index % tags.length], tags[(index + 1) % tags.length]);
                reviewTags.forEach(tag -> fixture.insertTag(reviewId, tag.name()));
                if (reverseWritten) {
                    fixture.insertReview(basecampId, revieweeId, reviewerId, true, NOW);
                }
                String comment = jdbc.queryForObject(
                        "SELECT comment FROM companion_review WHERE id = ?", String.class, reviewId);
                cases.add(new ReviewCase(reviewId, comment, completedAt, reverseWritten, rejoinWanted, reviewTags));
                index++;
            }
        }
        return cases;
    }

    private static Map<CompanionReviewTag, Long> expectedTagCounts(List<ReviewCase> revealed) {
        Map<CompanionReviewTag, Long> counts = new EnumMap<>(CompanionReviewTag.class);
        revealed.forEach(reviewCase -> reviewCase.tags().forEach(tag -> counts.merge(tag, 1L, Long::sum)));
        return counts;
    }

    private record ReviewCase(
            long reviewId,
            String comment,
            Instant completedAt,
            boolean reverseWritten,
            boolean rejoinWanted,
            List<CompanionReviewTag> tags) {

        boolean revealed() {
            return CompanionReviewPolicy.isRevealed(reverseWritten, completedAt, NOW);
        }
    }
}
