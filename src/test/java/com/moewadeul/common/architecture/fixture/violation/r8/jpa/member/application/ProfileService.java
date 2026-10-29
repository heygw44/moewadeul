package com.moewadeul.common.architecture.fixture.violation.r8.jpa.member.application;

import com.moewadeul.common.architecture.fixture.violation.r8.jpa.member.infra.ProfileStore;

public record ProfileService(ProfileStore profileStore) {}
