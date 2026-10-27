package com.moewadeul.basecamp.infra.query;

import java.time.LocalDate;

public record InProgressScheduleRow(long basecampId, LocalDate startDate, LocalDate endDate) {}
