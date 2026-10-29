package com.moewadeul.basecamp.domain;

import java.time.LocalDate;

public record BasecampSchedule(long basecampId, LocalDate startDate, LocalDate endDate) {}
