package com.moewadeul.program.infra;

import java.time.Instant;

public interface ProgramSeatHolderCounter {

    long countSeatHolders(long programId, Instant now);
}
