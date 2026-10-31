package com.moewadeul.program.application.enrollment;

import com.moewadeul.common.error.BusinessException;
import com.moewadeul.common.transaction.ReadCommittedTransaction;
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

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public boolean subscribe(long memberId, long programId) {
        ReadCommittedTransaction.require("알림 신청 뒤 자리를 다시 세는 읽기가 트랜잭션 처음의 낡은 스냅숏이 아니라 최신 커밋을 봐야 한다.");
        Program program = findProgram(programId);
        Instant now = clock.instant();
        requireFull(program, now);
        boolean created = vacancyAlertRepository.subscribe(programId, memberId, now) == 1;
        // INSERT는 외래 키 검사가 자리 반환의 행사 행 잠금에 막혀 반환이 커밋된 뒤에 들어가므로, 그 반환을 놓치지 않게 다시 센다.
        requireFull(program, now);
        log.info("program vacancy alert subscribed programId={} memberId={} created={}", programId, memberId, created);
        return created;
    }

    @Transactional
    public void unsubscribe(long memberId, long programId) {
        findProgram(programId);
        vacancyAlertRepository.unsubscribe(programId, memberId);
        log.info("program vacancy alert unsubscribed programId={} memberId={}", programId, memberId);
    }

    @Transactional
    public void unsubscribeAll(long memberId) {
        vacancyAlertRepository.deleteByMember(memberId);
        log.info("program vacancy alerts unsubscribed all memberId={}", memberId);
    }

    private void requireFull(Program program, Instant now) {
        program.requireVacancyAlertAcceptableAt(now, applicationRepository.countSeatHolders(program.getId(), now));
    }

    private Program findProgram(long programId) {
        return programRepository.findById(programId).orElseThrow(BusinessException::notFound);
    }
}
