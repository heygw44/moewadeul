package com.moewadeul.trust.domain.identity;

public interface IdentityProvider {

    VerifiedIdentity verify(IdentityClaim claim);

    IdentityProviderType type();
}
