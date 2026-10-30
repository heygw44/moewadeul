package com.moewadeul.program.application.enrollment;

import com.moewadeul.common.error.BusinessException;
import com.moewadeul.program.domain.Program;
import com.moewadeul.program.domain.ProgramApplication;
import com.moewadeul.program.domain.ProgramApplicationRepository;
import com.moewadeul.program.domain.ProgramRepository;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

// 신청(ProgramApplyService)은 행사 행을 먼저 잠그고 자리를 센다. 반복 가능한 읽기에서 그 SELECT가 보조 인덱스와 기본 키에 공유 잠금을 걸어서,
// 자리를 쥐는지 바꾸는 전이가 신청 행만 먼저 잡으면 데드락이 나거나 신청이 진행 중인 결제를 보지 못해 정원이 넘는다. 전이도 행사 행부터 잠근다.
// program_id를 잠금 없이 먼저 읽으므로 스냅숏이 이때 잡힌다. 잠근 뒤에 최신 커밋을 봐야 하는 읽기는 잠금 읽기로 해야 한다.
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
