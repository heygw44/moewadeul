package com.moewadeul.member.api.signup;

import com.moewadeul.member.domain.signup.AdminDisposableEmailDomain;
import java.time.Instant;

public record DisposableEmailDomainAdminResponse(long id, String domain, Instant createdAt) {

    static DisposableEmailDomainAdminResponse from(AdminDisposableEmailDomain domain) {
        return new DisposableEmailDomainAdminResponse(domain.getId(), domain.getDomain(), domain.getCreatedAt());
    }
}
