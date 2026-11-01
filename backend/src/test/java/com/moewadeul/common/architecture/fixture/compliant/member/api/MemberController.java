package com.moewadeul.common.architecture.fixture.compliant.member.api;

import com.moewadeul.common.architecture.fixture.compliant.common.support.Slug;
import com.moewadeul.common.architecture.fixture.compliant.member.application.MemberLookup;

public record MemberController(MemberLookup memberLookup, Slug slug) {}
