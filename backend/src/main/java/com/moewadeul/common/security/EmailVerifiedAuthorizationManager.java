package com.moewadeul.common.security;

import java.util.function.Supplier;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authorization.AuthorityAuthorizationManager;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.authorization.AuthorizationResult;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;

final class EmailVerifiedAuthorizationManager implements AuthorizationManager<RequestAuthorizationContext> {

    private final AuthorizationManager<RequestAuthorizationContext> hasEmailVerifiedAuthority =
            AuthorityAuthorizationManager.hasAuthority(LoginMember.AUTHORITY_EMAIL_VERIFIED);

    @Override
    public AuthorizationResult authorize(
            Supplier<? extends Authentication> authentication, RequestAuthorizationContext context) {
        AuthorizationResult result = hasEmailVerifiedAuthority.authorize(authentication, context);
        if (result != null && result.isGranted()) {
            return result;
        }
        return DenialReason.EMAIL_VERIFICATION_REQUIRED;
    }

    static boolean isEmailVerificationRequired(AccessDeniedException exception) {
        return exception instanceof AuthorizationDeniedException denied
                && denied.getAuthorizationResult() == DenialReason.EMAIL_VERIFICATION_REQUIRED;
    }

    private enum DenialReason implements AuthorizationResult {
        EMAIL_VERIFICATION_REQUIRED;

        @Override
        public boolean isGranted() {
            return false;
        }
    }
}
