package com.moewadeul.basecamp.application.lifecycle;

import com.moewadeul.basecamp.infra.lifecycle.BasecampAutoTransitionMapper;
import com.moewadeul.common.time.KoreaTime;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.function.BiFunction;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class BasecampAutoTransitionService {

    static final int BATCH_SIZE = 500;

    private final BasecampAutoTransitionMapper autoTransitionMapper;
    private final BasecampAutoTransitionApplier applier;
    private final Clock clock;

    public Result run() {
        Instant now = clock.instant();
        LocalDate today = LocalDate.ofInstant(now, KoreaTime.ZONE);
        Outcome completed = processAll(
                (afterId, limit) -> autoTransitionMapper.selectDueForCompletionIds(today, afterId, limit),
                basecampId -> applier.complete(basecampId, today, now));
        LocalDate dueStartDate = today.plusDays(1);
        Outcome departed = processAll(
                (afterId, limit) -> autoTransitionMapper.selectDueForDepartureIds(dueStartDate, afterId, limit),
                basecampId -> applier.processDayBeforeDeparture(basecampId, now));
        int failed = completed.failed() + departed.failed();
        log.info(
                "basecamp auto transition completed={} departureProcessed={} failed={}",
                completed.processed(),
                departed.processed(),
                failed);
        return new Result(completed.processed(), departed.processed(), failed);
    }

    private Outcome processAll(BiFunction<Long, Integer, List<Long>> selector, BasecampProcessor processor) {
        int processed = 0;
        int failed = 0;
        long afterId = 0;
        while (true) {
            List<Long> ids = selector.apply(afterId, BATCH_SIZE);
            for (long basecampId : ids) {
                switch (processOne(processor, basecampId)) {
                    case PROCESSED -> processed++;
                    case FAILED -> failed++;
                    case UNCHANGED -> {}
                }
            }
            if (ids.size() < BATCH_SIZE) {
                return new Outcome(processed, failed);
            }
            afterId = ids.get(ids.size() - 1);
        }
    }

    private ItemResult processOne(BasecampProcessor processor, long basecampId) {
        try {
            return processor.process(basecampId) ? ItemResult.PROCESSED : ItemResult.UNCHANGED;
        } catch (RuntimeException e) {
            log.error("basecamp auto transition failed basecampId={}", basecampId, e);
            return ItemResult.FAILED;
        }
    }

    @FunctionalInterface
    private interface BasecampProcessor {

        boolean process(long basecampId);
    }

    private enum ItemResult {
        PROCESSED,
        UNCHANGED,
        FAILED
    }

    private record Outcome(int processed, int failed) {}

    public record Result(int completed, int departureProcessed, int failed) {}
}
