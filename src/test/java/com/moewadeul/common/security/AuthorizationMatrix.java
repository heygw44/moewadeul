package com.moewadeul.common.security;

import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.params.provider.Arguments;
import org.springframework.http.HttpMethod;

final class AuthorizationMatrix {

    record Endpoint(HttpMethod method, String path) {}

    static final List<Endpoint> PUBLIC = List.of(
            new Endpoint(HttpMethod.POST, "/api/members"),
            new Endpoint(HttpMethod.POST, "/api/auth/login"),
            new Endpoint(HttpMethod.POST, "/api/auth/password-reset/request"),
            new Endpoint(HttpMethod.POST, "/api/auth/password-reset/confirm"),
            new Endpoint(HttpMethod.GET, "/api/members/1/profile"),
            new Endpoint(HttpMethod.GET, "/api/spots"),
            new Endpoint(HttpMethod.GET, "/api/spots/nearby"),
            new Endpoint(HttpMethod.GET, "/api/spots/1"),
            new Endpoint(HttpMethod.GET, "/api/spots/1/reviews"),
            new Endpoint(HttpMethod.GET, "/api/spots/1/weather"),
            new Endpoint(HttpMethod.GET, "/api/basecamps"),
            new Endpoint(HttpMethod.GET, "/api/basecamps/1"),
            new Endpoint(HttpMethod.GET, "/api/programs"),
            new Endpoint(HttpMethod.GET, "/api/programs/1"),
            new Endpoint(HttpMethod.GET, "/api/community/posts"),
            new Endpoint(HttpMethod.GET, "/api/community/posts/1"),
            new Endpoint(HttpMethod.GET, "/api/community/posts/1/comments"),
            new Endpoint(HttpMethod.GET, "/api/memories/feed"),
            new Endpoint(HttpMethod.GET, "/api/memories/1"),
            new Endpoint(HttpMethod.GET, "/api/members/1/memories"));

    static final List<Endpoint> LOGIN = List.of(
            new Endpoint(HttpMethod.GET, "/api/me"),
            new Endpoint(HttpMethod.DELETE, "/api/me"),
            new Endpoint(HttpMethod.PUT, "/api/me/password"),
            new Endpoint(HttpMethod.GET, "/api/me/trust"),
            new Endpoint(HttpMethod.GET, "/api/me/basecamps"),
            new Endpoint(HttpMethod.GET, "/api/me/companion-reviews/received"),
            new Endpoint(HttpMethod.GET, "/api/me/program-applications"),
            new Endpoint(HttpMethod.GET, "/api/me/solo-trips"),
            new Endpoint(HttpMethod.GET, "/api/me/memories"),
            new Endpoint(HttpMethod.GET, "/api/me/notifications"),
            new Endpoint(HttpMethod.GET, "/api/me/notifications/unread-count"),
            new Endpoint(HttpMethod.POST, "/api/me/notifications/1/read"),
            new Endpoint(HttpMethod.POST, "/api/me/notifications/read-all"),
            new Endpoint(HttpMethod.GET, "/api/me/notification-settings"),
            new Endpoint(HttpMethod.POST, "/api/auth/logout"));

    static final List<Endpoint> LOGIN_EMAIL_VERIFICATION = List.of(
            new Endpoint(HttpMethod.POST, "/api/me/email-verification"),
            new Endpoint(HttpMethod.POST, "/api/me/email-verification/resend"));

    static final List<Endpoint> VERIFIED = List.of(
            new Endpoint(HttpMethod.PATCH, "/api/me"),
            new Endpoint(HttpMethod.PUT, "/api/me/notification-settings"),
            new Endpoint(HttpMethod.POST, "/api/me/identity-verification"),
            new Endpoint(HttpMethod.POST, "/api/bakjis"),
            new Endpoint(HttpMethod.PATCH, "/api/bakjis/1"),
            new Endpoint(HttpMethod.DELETE, "/api/bakjis/1"),
            new Endpoint(HttpMethod.POST, "/api/bakjis/1/confirmations"),
            new Endpoint(HttpMethod.POST, "/api/bakjis/1/reports"),
            new Endpoint(HttpMethod.POST, "/api/spots/1/reviews"),
            new Endpoint(HttpMethod.POST, "/api/spots/ai-interpret"),
            new Endpoint(HttpMethod.PATCH, "/api/reviews/1"),
            new Endpoint(HttpMethod.DELETE, "/api/reviews/1"),
            new Endpoint(HttpMethod.POST, "/api/reviews/images"),
            new Endpoint(HttpMethod.POST, "/api/solo-trips"),
            new Endpoint(HttpMethod.DELETE, "/api/solo-trips/1"),
            new Endpoint(HttpMethod.POST, "/api/memories"),
            new Endpoint(HttpMethod.PATCH, "/api/memories/1"),
            new Endpoint(HttpMethod.DELETE, "/api/memories/1"),
            new Endpoint(HttpMethod.POST, "/api/memories/images"),
            new Endpoint(HttpMethod.POST, "/api/basecamps"),
            new Endpoint(HttpMethod.PATCH, "/api/basecamps/1"),
            new Endpoint(HttpMethod.POST, "/api/basecamps/1/close"),
            new Endpoint(HttpMethod.POST, "/api/basecamps/1/reopen"),
            new Endpoint(HttpMethod.POST, "/api/basecamps/1/confirm"),
            new Endpoint(HttpMethod.POST, "/api/basecamps/1/cancel"),
            new Endpoint(HttpMethod.PUT, "/api/basecamps/1/contact"),
            new Endpoint(HttpMethod.POST, "/api/basecamps/1/applications"),
            new Endpoint(HttpMethod.DELETE, "/api/basecamps/1/applications/me"),
            new Endpoint(HttpMethod.GET, "/api/basecamps/1/applications"),
            new Endpoint(HttpMethod.POST, "/api/basecamps/1/applications/2/approve"),
            new Endpoint(HttpMethod.POST, "/api/basecamps/1/applications/2/reject"),
            new Endpoint(HttpMethod.DELETE, "/api/basecamps/1/members/me"),
            new Endpoint(HttpMethod.POST, "/api/basecamps/1/members/2/kick"),
            new Endpoint(HttpMethod.GET, "/api/me/companion-reviews/pending"),
            new Endpoint(HttpMethod.POST, "/api/basecamps/1/companion-reviews"),
            new Endpoint(HttpMethod.GET, "/api/members/1/companion-reviews"),
            new Endpoint(HttpMethod.POST, "/api/member-reports"),
            new Endpoint(HttpMethod.POST, "/api/programs/1/applications"),
            new Endpoint(HttpMethod.POST, "/api/program-applications/1/pay"),
            new Endpoint(HttpMethod.POST, "/api/program-applications/1/cancel"),
            new Endpoint(HttpMethod.POST, "/api/programs/1/vacancy-alerts"),
            new Endpoint(HttpMethod.DELETE, "/api/programs/1/vacancy-alerts"),
            new Endpoint(HttpMethod.POST, "/api/community/posts"),
            new Endpoint(HttpMethod.PATCH, "/api/community/posts/1"),
            new Endpoint(HttpMethod.DELETE, "/api/community/posts/1"),
            new Endpoint(HttpMethod.POST, "/api/community/images"),
            new Endpoint(HttpMethod.POST, "/api/community/posts/1/comments"),
            new Endpoint(HttpMethod.PATCH, "/api/community/comments/1"),
            new Endpoint(HttpMethod.DELETE, "/api/community/comments/1"),
            new Endpoint(HttpMethod.PUT, "/api/community/posts/1/like"),
            new Endpoint(HttpMethod.DELETE, "/api/community/posts/1/like"),
            new Endpoint(HttpMethod.PUT, "/api/memories/1/like"),
            new Endpoint(HttpMethod.DELETE, "/api/memories/1/like"),
            new Endpoint(HttpMethod.POST, "/api/community/posts/1/reports"),
            new Endpoint(HttpMethod.POST, "/api/community/comments/1/reports"),
            new Endpoint(HttpMethod.POST, "/api/memories/1/reports"));

    static final List<Endpoint> ADMIN = List.of(
            new Endpoint(HttpMethod.POST, "/api/admin/programs"),
            new Endpoint(HttpMethod.PATCH, "/api/admin/programs/1"),
            new Endpoint(HttpMethod.POST, "/api/admin/programs/1/cancel"),
            new Endpoint(HttpMethod.GET, "/api/admin/programs/1/applications"),
            new Endpoint(HttpMethod.GET, "/api/admin/member-reports"),
            new Endpoint(HttpMethod.GET, "/api/admin/member-reports/1"),
            new Endpoint(HttpMethod.POST, "/api/admin/member-reports/1/start-review"),
            new Endpoint(HttpMethod.POST, "/api/admin/member-reports/1/action"),
            new Endpoint(HttpMethod.POST, "/api/admin/member-reports/1/dismiss"),
            new Endpoint(HttpMethod.POST, "/api/admin/sanctions/1/lift"),
            new Endpoint(HttpMethod.GET, "/api/admin/spots"),
            new Endpoint(HttpMethod.POST, "/api/admin/spots/1/hide"),
            new Endpoint(HttpMethod.POST, "/api/admin/spots/1/restore"),
            new Endpoint(HttpMethod.POST, "/api/admin/sync-jobs"),
            new Endpoint(HttpMethod.GET, "/api/admin/sync-jobs"),
            new Endpoint(HttpMethod.GET, "/api/admin/community/posts"),
            new Endpoint(HttpMethod.POST, "/api/admin/community/posts/1/hide"),
            new Endpoint(HttpMethod.POST, "/api/admin/community/posts/1/restore"),
            new Endpoint(HttpMethod.GET, "/api/admin/community/comments"),
            new Endpoint(HttpMethod.POST, "/api/admin/community/comments/1/hide"),
            new Endpoint(HttpMethod.POST, "/api/admin/community/comments/1/restore"),
            new Endpoint(HttpMethod.GET, "/api/admin/memories"),
            new Endpoint(HttpMethod.POST, "/api/admin/memories/1/hide"),
            new Endpoint(HttpMethod.POST, "/api/admin/memories/1/restore"),
            new Endpoint(HttpMethod.GET, "/api/admin/audit-logs"));

    private AuthorizationMatrix() {}

    static Stream<Arguments> publicEndpoints() {
        return arguments(PUBLIC);
    }

    static Stream<Arguments> loginLevelEndpoints() {
        return arguments(LOGIN);
    }

    static Stream<Arguments> allLoginLevelEndpoints() {
        return Stream.concat(arguments(LOGIN), arguments(LOGIN_EMAIL_VERIFICATION));
    }

    static Stream<Arguments> verifiedOnlyEndpoints() {
        return arguments(VERIFIED);
    }

    static Stream<Arguments> adminEndpoints() {
        return arguments(ADMIN);
    }

    static Stream<Endpoint> all() {
        return Stream.of(PUBLIC, LOGIN, LOGIN_EMAIL_VERIFICATION, VERIFIED, ADMIN)
                .flatMap(List::stream);
    }

    private static Stream<Arguments> arguments(List<Endpoint> endpoints) {
        return endpoints.stream().map(endpoint -> Arguments.of(endpoint.method(), endpoint.path()));
    }
}
