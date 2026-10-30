package com.moewadeul.program.application.enrollment;

import com.moewadeul.common.error.BusinessException;
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
