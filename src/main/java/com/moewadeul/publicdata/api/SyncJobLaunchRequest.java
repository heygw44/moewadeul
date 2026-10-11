package com.moewadeul.publicdata.api;

import com.moewadeul.publicdata.domain.SyncJobType;
import jakarta.validation.constraints.NotNull;

public record SyncJobLaunchRequest(@NotNull SyncJobType jobType) {}
