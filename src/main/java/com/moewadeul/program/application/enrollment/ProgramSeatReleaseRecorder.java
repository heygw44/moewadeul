package com.moewadeul.program.application.enrollment;

import com.moewadeul.common.outbox.OutboxEventRecorder;
import com.moewadeul.program.domain.Program;
import com.moewadeul.program.domain.ProgramApplicationRepository;
import com.moewadeul.program.domain.ProgramPhase;
import com.moewadeul.program.domain.ProgramVacancyAlertRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ProgramSeatReleaseRecorder {

    // 운영 Gmail 계정의 하루 발송 한도(500통)를 만료가 몰린 한 번의 실행이 다 쓰지 않도록, 같은 회원에게는 이 간격 안에 다시 알리지 않는다.
    static final Duration RENOTIFY_INTERVAL = Duration.ofMinutes(30);

    private final ProgramVacancyAlertRepository vacancyAlertRepository;
    private final ProgramApplicationRepository applicationRepository;
    private final OutboxEventRecorder outboxEventRecorder;

    public void record(Program program, Instant now) {
        ProgramPhase phase = program.phaseAt(now);
        if (phase == ProgramPhase.CANCELED || phase == ProgramPhase.CLOSED) {
            return;
        }
        long programId = program.getId();
        Instant notifiedBefore = now.minus(RENOTIFY_INTERVAL);
        if (vacancyAlertRepository.findAlertTargets(programId, notifiedBefore).isEmpty()) {
            return;
        }
        if (applicationRepository.countSeatHolders(programId, now) >= program.getCapacity()) {
            return;
        }
        List<Long> targets = vacancyAlertRepository.lockAlertTargets(programId, notifiedBefore);
        if (targets.isEmpty()) {
            return;
        }
        vacancyAlertRepository.markNotified(programId, targets, now);
        outboxEventRecorder.record(
                ProgramSeatEvents.SEAT_RELEASED_EVENT_TYPE,
                ProgramSeatEvents.AGGREGATE_TYPE,
                programId,
                new ProgramSeatEvents.SeatReleasedPayload(programId, targets));
        log.info("program seat released programId={} targetCount={}", programId, targets.size());
    }
}
