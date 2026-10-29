package com.moewadeul.program.application.enrollment;

import com.moewadeul.common.error.BusinessException;
import com.moewadeul.common.error.CommonErrorCode;
import com.moewadeul.program.domain.Program;
import com.moewadeul.program.domain.ProgramErrorCode;
import com.moewadeul.program.domain.ProgramPhase;
import com.moewadeul.program.domain.ProgramRepository;
import com.moewadeul.program.domain.ProgramVacancyAlertRepository;
import java.time.Clock;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProgramVacancyAlertService {

    private final ProgramRepository programRepository;
    private final ProgramVacancyAlertRepository vacancyAlertRepository;
    private final Clock clock;

    @Transactional
    public boolean subscribe(long memberId, long programId) {
        Program program = findProgram(programId);
        Instant now = clock.instant();
        ProgramPhase phase = program.phaseAt(now);
        if (phase == ProgramPhase.CANCELED || phase == ProgramPhase.CLOSED) {
            throw new BusinessException(ProgramErrorCode.PROGRAM_INVALID_STATE);
        }
        boolean created = vacancyAlertRepository.subscribe(programId, memberId, now) == 1;
        log.info("program vacancy alert subscribed programId={} memberId={} created={}", programId, memberId, created);
        return created;
    }

    @Transactional
    public void unsubscribe(long memberId, long programId) {
        findProgram(programId);
        vacancyAlertRepository.unsubscribe(programId, memberId);
        log.info("program vacancy alert unsubscribed programId={} memberId={}", programId, memberId);
    }

    private Program findProgram(long programId) {
        return programRepository
                .findById(programId)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));
    }
}
