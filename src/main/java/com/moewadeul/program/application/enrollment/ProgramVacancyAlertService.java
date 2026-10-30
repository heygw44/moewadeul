package com.moewadeul.program.application.enrollment;

import com.moewadeul.common.error.BusinessException;
import com.moewadeul.program.domain.Program;
import com.moewadeul.program.domain.ProgramApplicationRepository;
import com.moewadeul.program.domain.ProgramRepository;
import com.moewadeul.program.domain.ProgramVacancyAlertRepository;
import java.time.Clock;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProgramVacancyAlertService {

    private final ProgramRepository programRepository;
    private final ProgramVacancyAlertRepository vacancyAlertRepository;
    private final ProgramApplicationRepository applicationRepository;
    private final Clock clock;

    // 취소의 잠금 읽기에 막혔다가 들어간 신청은 취소가 커밋된 뒤에야 INSERT되므로, 행을 넣은 뒤 자리를 다시 세어야 한다.
    // READ COMMITTED여야 이 재조회가 트랜잭션 처음의 스냅숏이 아니라 최신 커밋을 본다.
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public boolean subscribe(long memberId, long programId) {
        Program program = findProgram(programId);
        Instant now = clock.instant();
        program.requireVacancyAlertAcceptableAt(now, applicationRepository.countSeatHolders(programId, now));
        boolean created = vacancyAlertRepository.subscribe(programId, memberId, now) == 1;
        program.requireVacancyAlertAcceptableAt(now, applicationRepository.countSeatHolders(programId, now));
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
        return programRepository.findById(programId).orElseThrow(BusinessException::notFound);
    }
}
