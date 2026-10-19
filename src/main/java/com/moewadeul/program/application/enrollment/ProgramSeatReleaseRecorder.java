package com.moewadeul.program.application.enrollment;

import com.moewadeul.common.outbox.OutboxEventRecorder;
import com.moewadeul.program.domain.Program;
import com.moewadeul.program.domain.ProgramPhase;
import com.moewadeul.program.domain.ProgramVacancyAlertRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 본인 취소나 결제 만료로 행사에 자리가 돌아왔을 때, 빈자리 알림 신청자에게 알릴 이벤트를 기록한다.
 * 호출하는 쪽의 트랜잭션 안에서 불러야 한다. 그래야 자리를 돌려주는 변경이 롤백되면 이벤트도 함께 사라진다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProgramSeatReleaseRecorder {

    // 마지막 알림 뒤 이 시간 안에 자리가 또 돌아오면 그 회원에게는 다시 알리지 않는다.
    // 결제 기한이 같은 신청 수백 건이 한 번의 만료 실행에서 함께 만료되면 자리 반환도 수백 번 일어난다.
    // 반환마다 알리면 알림함 행과 빈자리 메일이 반환 수만큼 곱해져, 운영 메일 계정의 하루 발송 한도(500통)를 한 번에 다 쓸 수 있다.
    // 그러면 그날 인증 코드와 비밀번호 재설정 메일이 나가지 않는다. 그래서 같은 회원에게는 30분에 한 번만 알린다.
    static final Duration RENOTIFY_INTERVAL = Duration.ofMinutes(30);

    private final ProgramVacancyAlertRepository vacancyAlertRepository;
    private final OutboxEventRecorder outboxEventRecorder;

    /**
     * 호출하면 알림 대상 회원이 있을 때 알림 시각을 now로 적고 이벤트를 기록한다. 대상은 알림 신청자 중 결제 대기·확정 신청이 없고,
     * 최근 30분 안에 빈자리 알림을 받지 않은 회원이다.
     * 취소됐거나 신청 마감 시각이 지난 행사는 자리가 돌아와도 새로 신청받지 않으므로 아무것도 하지 않는다. 대상이 없을 때도 마찬가지다.
     */
    public void record(Program program, Instant now) {
        ProgramPhase phase = program.phaseAt(now);
        if (phase == ProgramPhase.CANCELED || phase == ProgramPhase.CLOSED) {
            return;
        }
        long programId = program.getId();
        Instant notifiedBefore = now.minus(RENOTIFY_INTERVAL);
        // 먼저 잠그지 않고 읽는다. 만료가 몰리면 첫 반환이 커밋한 뒤의 반환은 모두 여기서 대상이 비어 잠금 없이 끝난다.
        if (vacancyAlertRepository.findAlertTargets(programId, notifiedBefore).isEmpty()) {
            return;
        }
        // 대상이 있으면 잠금 읽기로 다시 고른다. 두 반환이 같은 순간에 오면 둘 다 커밋 전의 알림 시각을 보고 같은 회원에게 알릴 수 있다.
        // 잠금 읽기를 하면 나중 트랜잭션이 앞 트랜잭션의 커밋을 기다렸다가 방금 적힌 알림 시각을 보고 그 회원을 뺀다.
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
