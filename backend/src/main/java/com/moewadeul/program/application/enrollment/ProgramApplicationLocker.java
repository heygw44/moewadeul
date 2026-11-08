package com.moewadeul.program.application.enrollment;

import com.moewadeul.common.error.BusinessException;
import com.moewadeul.common.transaction.ReadCommittedTransaction;
import com.moewadeul.program.domain.Program;
import com.moewadeul.program.domain.ProgramApplication;
import com.moewadeul.program.domain.ProgramApplicationRepository;
import com.moewadeul.program.domain.ProgramRepository;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class ProgramApplicationLocker {

    private final ProgramRepository programRepository;
    private final ProgramApplicationRepository applicationRepository;

    Optional<LockedApplication> lock(long applicationId) {
        ReadCommittedTransaction.require("잠그기 전의 행사 번호 읽기가 연 스냅숏을 잠근 뒤의 일반 읽기가 보지 않아야 한다.");
        Optional<Long> programId = applicationRepository.findProgramId(applicationId);
        if (programId.isEmpty()) {
            return Optional.empty();
        }
        Program program = programRepository.findByIdForUpdate(programId.get()).orElseThrow(BusinessException::notFound);
        return applicationRepository
                .findByIdForUpdate(applicationId)
                .map(application -> new LockedApplication(program, application));
    }

    record LockedApplication(Program program, ProgramApplication application) {}
}
