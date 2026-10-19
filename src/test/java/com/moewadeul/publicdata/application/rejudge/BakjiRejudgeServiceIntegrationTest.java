package com.moewadeul.publicdata.application.rejudge;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.moewadeul.common.testsupport.IntegrationTest;
import com.moewadeul.publicdata.application.SyncJobRunService;
import com.moewadeul.publicdata.application.park.ParkBoundaryLoadService;
import com.moewadeul.publicdata.domain.SyncJobType;
import com.moewadeul.spot.application.park.BakjiRejudgeChunk;
import com.moewadeul.spot.application.park.ParkAreaJudgeService;
import com.moewadeul.spot.infra.park.ParkAreaJudgeMapper;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

// 픽스처의 경계는 테스트에서 지어낸 작은 사각형이다. 좌표는 (경도 위도) 순서다.
// parks_20251231.tsv: 북한산 경도 126.9~127.0, 위도 37.6~37.7
// parks_20260630.tsv: 북한산을 경도 127.1~127.2로 옮겼다. 적재 서비스는 같은 공원 행의 경계를 덮어쓰므로 ID는 그대로다.
@IntegrationTest
class BakjiRejudgeServiceIntegrationTest {

    private static final String FIXTURE = "fixtures/publicdata/park-boundary/parks_20251231.tsv";
    private static final String NEWER_FIXTURE = "fixtures/publicdata/park-boundary/parks_20260630.tsv";

    @Autowired
    private BakjiRejudgeService bakjiRejudgeService;

    @Autowired
    private ParkBoundaryLoadService parkBoundaryLoadService;

    @Autowired
    private SyncJobRunService syncJobRunService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ParkAreaJudgeService parkAreaJudgeService;

    @Autowired
    private ParkAreaJudgeMapper parkAreaJudgeMapper;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private Clock clock;

    @Test
    @DisplayName("[F-06] 공원 경계를 바꾼 뒤 재판정하면 박지의 공원 경고가 새 경계와 일치한다")
    void rejudgeMatchesNewBoundaries() throws IOException {
        // given
        parkBoundaryLoadService.load(fixture(FIXTURE)).orElseThrow();
        long bukhansanId = idOf("북한산");
        long oldInside = insertBakji("옛 경계 안 박지", 37.65, 126.95);
        long newInside = insertBakji("새 경계 안 박지", 37.65, 127.15);
        long outside = insertBakji("경계 밖 박지", 35.0, 129.0);

        // when
        BakjiRejudgeResult first = bakjiRejudgeService.rejudge().orElseThrow();

        // then
        assertThat(first.judgedCount()).isEqualTo(3);
        assertThat(first.newlyWarnedCount()).isEqualTo(1);
        assertThat(first.clearedCount()).isZero();
        assertWarned(oldInside, bukhansanId);
        assertNotWarned(newInside);
        assertNotWarned(outside);

        // when: 북한산 경계를 옮긴 파일을 적재하고 다시 판정한다.
        parkBoundaryLoadService.load(fixture(NEWER_FIXTURE)).orElseThrow();
        BakjiRejudgeResult second = bakjiRejudgeService.rejudge().orElseThrow();

        // then
        assertThat(second.judgedCount()).isEqualTo(3);
        assertThat(second.newlyWarnedCount()).isEqualTo(1);
        assertThat(second.clearedCount()).isEqualTo(1);
        assertThat(idOf("북한산")).isEqualTo(bukhansanId);
        assertNotWarned(oldInside);
        assertWarned(newInside, bukhansanId);
        assertNotWarned(outside);
    }

    @Test
    @DisplayName("[F-06] 재판정이 끝나면 실행 기록이 COMPLETED가 되고 처리 건수가 박지 수와 같다")
    void rejudgeCompletesRunWithBakjiCount() throws IOException {
        // given
        parkBoundaryLoadService.load(fixture(FIXTURE)).orElseThrow();
        insertBakji("박지 하나", 37.65, 126.95);
        insertBakji("박지 둘", 35.0, 129.0);

        // when
        bakjiRejudgeService.rejudge().orElseThrow();

        // then
        Map<String, Object> run = jdbcTemplate.queryForMap(
                "SELECT status, processed_count FROM sync_job_run WHERE job_type = 'BAKJI_REJUDGE'");
        assertThat(run.get("status")).isEqualTo("COMPLETED");
        assertThat(((Number) run.get("processed_count")).intValue()).isEqualTo(2);
    }

    @Test
    @DisplayName("[F-06] 다른 박지 재판정이 실행 중이면 시작하지 않고 빈 값을 돌려준다")
    void rejudgeReturnsEmptyWhileAnotherRunIsInProgress() {
        // given
        syncJobRunService
                .begin(SyncJobType.BAKJI_REJUDGE, Duration.ofMinutes(30))
                .orElseThrow();

        // when, then
        assertThat(bakjiRejudgeService.rejudge()).isEmpty();
        assertThat(jdbcTemplate.queryForList(
                        "SELECT status FROM sync_job_run WHERE job_type = 'BAKJI_REJUDGE'", String.class))
                .containsExactly("RUNNING");
    }

    @Test
    @DisplayName("[F-06] 재판정은 박지를 묶음 크기만큼씩 나눠 모두 판정하고, 묶음마다 실행 기록에 처리 수와 마지막 박지 ID를 남긴다")
    void rejudgeProcessesBakjisInChunks() throws IOException {
        // given: 묶음 크기 2에 박지 5건이면 묶음 셋이다.
        parkBoundaryLoadService.load(fixture(FIXTURE)).orElseThrow();
        long bukhansanId = idOf("북한산");
        List<Long> insideIds = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            insideIds.add(insertBakji("경계 안 박지 " + i, 37.65, 126.95));
        }
        long outsideFirst = insertBakji("경계 밖 박지 1", 35.0, 129.0);
        long outsideLast = insertBakji("경계 밖 박지 2", 35.0, 129.0);
        List<Long> chunkStarts = new ArrayList<>();
        BakjiRejudgeService service = serviceWithChunkSize(2, (afterId, limit) -> chunkStarts.add(afterId));

        // when
        BakjiRejudgeResult result = service.rejudge().orElseThrow();

        // then: 마지막 호출은 남은 박지가 없음을 확인하는 빈 묶음이다.
        assertThat(result).isEqualTo(new BakjiRejudgeResult(5, 3, 0));
        assertThat(chunkStarts).containsExactly(0L, insideIds.get(1), outsideFirst, outsideLast);
        insideIds.forEach(id -> assertWarned(id, bukhansanId));
        assertNotWarned(outsideFirst);
        assertNotWarned(outsideLast);
        Map<String, Object> run = jdbcTemplate.queryForMap(
                "SELECT status, processed_count, progress_cursor FROM sync_job_run WHERE job_type = 'BAKJI_REJUDGE'");
        assertThat(run.get("status")).isEqualTo("COMPLETED");
        assertThat(((Number) run.get("processed_count")).intValue()).isEqualTo(5);
        assertThat(run.get("progress_cursor")).isEqualTo(String.valueOf(outsideLast));
    }

    @Test
    @DisplayName("[F-06][F-08] 재판정이 다음 묶음을 처리하는 동안 앞 묶음의 박지 행은 잠겨 있지 않다")
    void rejudgeReleasesRowLocksBetweenChunks() throws Exception {
        // given
        parkBoundaryLoadService.load(fixture(FIXTURE)).orElseThrow();
        long first = insertBakji("첫 묶음 박지", 37.65, 126.95);
        insertBakji("둘째 묶음 박지", 37.65, 126.95);
        ExecutorService other = Executors.newSingleThreadExecutor();
        AtomicReference<Long> lockedDuringSecondChunk = new AtomicReference<>();
        BakjiRejudgeService service = serviceWithChunkSize(1, (afterId, limit) -> {
            if (afterId == first) {
                lockedDuringSecondChunk.set(lockNowait(other, first));
            }
        });

        // when
        try {
            service.rejudge().orElseThrow();
        } finally {
            other.shutdown();
        }

        // then: 다른 커넥션이 기다리지 않고(NOWAIT) 앞 묶음의 박지 행을 잠갔다.
        assertThat(lockedDuringSecondChunk.get()).isEqualTo(first);
    }

    @Test
    @DisplayName("[F-06] 재판정이 중간 묶음에서 실패하면 실행 기록이 FAILED로 남고, 다음 재판정은 처음부터 모든 박지를 판정한다")
    void rejudgeRestartsFromBeginningAfterFailure() throws IOException {
        // given
        parkBoundaryLoadService.load(fixture(FIXTURE)).orElseThrow();
        long bukhansanId = idOf("북한산");
        long first = insertBakji("첫 묶음 박지", 37.65, 126.95);
        long second = insertBakji("둘째 묶음 박지", 37.65, 126.95);
        BakjiRejudgeService failing = serviceWithChunkSize(1, (afterId, limit) -> {
            if (afterId == first) {
                throw new IllegalStateException("둘째 묶음 실패");
            }
        });

        // when
        assertThatThrownBy(() -> failing.rejudge()).isInstanceOf(IllegalStateException.class);

        // then: 앞 묶음은 커밋됐고 둘째 묶음은 판정하지 않았다.
        assertWarned(first, bukhansanId);
        assertThat(judgement(second).get("area_checked_at")).isNull();

        // when: 같은 경계로 판정을 지운 뒤 다시 재판정한다.
        jdbcTemplate.update("UPDATE spot SET park_warning = FALSE, protected_area_id = NULL WHERE id = ?", first);
        List<Long> chunkStarts = new ArrayList<>();
        BakjiRejudgeResult result = serviceWithChunkSize(1, (afterId, limit) -> chunkStarts.add(afterId))
                .rejudge()
                .orElseThrow();

        // then
        assertThat(result).isEqualTo(new BakjiRejudgeResult(2, 2, 0));
        assertThat(chunkStarts.getFirst()).isZero();
        assertWarned(first, bukhansanId);
        assertWarned(second, bukhansanId);
        assertThat(jdbcTemplate.queryForList(
                        "SELECT status FROM sync_job_run WHERE job_type = 'BAKJI_REJUDGE' ORDER BY id", String.class))
                .containsExactly("FAILED", "COMPLETED");
    }

    // 묶음 크기를 바꾼 재판정 서비스를 만든다. 묶음 판정은 실제 빈에 맡기되, 묶음마다 판정하기 전에 beforeChunk를 부른다.
    private BakjiRejudgeService serviceWithChunkSize(int chunkSize, BiConsumer<Long, Integer> beforeChunk) {
        ParkAreaJudgeService hooked = new ParkAreaJudgeService(parkAreaJudgeMapper, clock) {
            @Override
            public BakjiRejudgeChunk rejudgeBakjiChunk(long afterId, int limit, Instant now) {
                beforeChunk.accept(afterId, limit);
                return parkAreaJudgeService.rejudgeBakjiChunk(afterId, limit, now);
            }
        };
        return new BakjiRejudgeService(
                syncJobRunService, hooked, new BakjiRejudgeProperties(Duration.ofMinutes(30), chunkSize), clock);
    }

    // 다른 스레드의 새 트랜잭션에서 박지 행을 기다리지 않고 잠근다. 다른 트랜잭션이 이미 잠갔으면 MySQL이 바로 오류를 낸다.
    private Long lockNowait(ExecutorService other, long spotId) {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        try {
            return other.submit(() -> tx.execute(status -> jdbcTemplate.queryForObject(
                            "SELECT id FROM spot WHERE id = ? FOR UPDATE NOWAIT", Long.class, spotId)))
                    .get(10, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private void assertWarned(long spotId, long protectedAreaId) {
        Map<String, Object> row = judgement(spotId);
        assertThat(row.get("park_warning")).isEqualTo(true);
        assertThat(((Number) row.get("protected_area_id")).longValue()).isEqualTo(protectedAreaId);
        assertThat(row.get("area_checked_at")).isNotNull();
    }

    private void assertNotWarned(long spotId) {
        Map<String, Object> row = judgement(spotId);
        assertThat(row.get("park_warning")).isEqualTo(false);
        assertThat(row.get("protected_area_id")).isNull();
        assertThat(row.get("area_checked_at")).isNotNull();
    }

    private Map<String, Object> judgement(long spotId) {
        return jdbcTemplate.queryForMap(
                "SELECT park_warning, protected_area_id, area_checked_at FROM spot WHERE id = ?", spotId);
    }

    // POINT는 (경도, 위도) 순서로 받는다.
    private long insertBakji(String name, double latitude, double longitude) {
        jdbcTemplate.update(
                "INSERT INTO spot (type, name, location, weather_nx, weather_ny, status, created_at, updated_at)"
                        + " VALUES ('BAKJI', ?, ST_SRID(POINT(?, ?), 4326), 60, 127, 'ACTIVE', NOW(6), NOW(6))",
                name,
                longitude,
                latitude);
        return jdbcTemplate.queryForObject("SELECT id FROM spot WHERE name = ?", Long.class, name);
    }

    private long idOf(String name) {
        return jdbcTemplate.queryForObject("SELECT id FROM protected_area WHERE name = ?", Long.class, name);
    }

    private static Path fixture(String path) throws IOException {
        return new ClassPathResource(path).getFile().toPath();
    }
}
