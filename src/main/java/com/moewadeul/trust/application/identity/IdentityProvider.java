package com.moewadeul.trust.application.identity;

import com.moewadeul.trust.domain.identity.IdentityClaim;
import com.moewadeul.trust.domain.identity.IdentityProviderType;
import com.moewadeul.trust.domain.identity.VerifiedIdentity;

public interface IdentityProvider {

    VerifiedIdentity verify(IdentityClaim claim);

    IdentityProviderType type();
}
