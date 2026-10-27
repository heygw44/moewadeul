package com.moewadeul.trust.api.profile;

import jakarta.validation.constraints.NotBlank;

record WithdrawRequest(@NotBlank String password) {}
