package com.moewadeul.program.application.catalog;

import com.moewadeul.program.domain.Program;

public final class ProgramLimits {

    public static final int TITLE_MAX_LENGTH = Program.TITLE_MAX_LENGTH;
    public static final int DESCRIPTION_MAX_LENGTH = Program.DESCRIPTION_MAX_LENGTH;
    public static final int LOCATION_TEXT_MAX_LENGTH = Program.LOCATION_TEXT_MAX_LENGTH;
    public static final int CAPACITY_MIN = Program.CAPACITY_MIN;
    public static final int CAPACITY_MAX = Program.CAPACITY_MAX;
    public static final int FEE_MIN = Program.FEE_MIN;
    public static final int FEE_MAX = Program.FEE_MAX;
    public static final int PAYMENT_DEADLINE_MINUTES_MIN = Program.PAYMENT_DEADLINE_MINUTES_MIN;
    public static final int PAYMENT_DEADLINE_MINUTES_MAX = Program.PAYMENT_DEADLINE_MINUTES_MAX;

    private ProgramLimits() {}
}
