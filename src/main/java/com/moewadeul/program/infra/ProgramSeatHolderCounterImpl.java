package com.moewadeul.program.infra;

import com.moewadeul.program.infra.enrollment.ProgramApplicationMapper;
import java.time.Instant;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
class ProgramSeatHolderCounterImpl implements ProgramSeatHolderCounter {

    private final ProgramApplicationMapper mapper;

    @Override
    public long countSeatHolders(long programId, Instant now) {
        return mapper.countSeatHolders(programId, now);
    }
}
