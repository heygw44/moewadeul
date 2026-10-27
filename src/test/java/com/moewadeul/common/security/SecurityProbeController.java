package com.moewadeul.common.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(SecurityProbeController.BASE_PATH)
class SecurityProbeController {

    static final String BASE_PATH = "/security-test";

    private final LoginSessionManager loginSessionManager;

    SecurityProbeController(LoginSessionManager loginSessionManager) {
        this.loginSessionManager = loginSessionManager;
    }

    @PostMapping("/session")
    ResponseEntity<Void> createSession(HttpServletRequest request) {
        request.getSession(true);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/login")
    ResponseEntity<Void> login(
            @RequestParam long memberId,
            @RequestParam String role,
            @RequestParam(defaultValue = "false") boolean emailVerified,
            HttpServletRequest request,
            HttpServletResponse response) {
        loginSessionManager.login(new LoginMember(memberId, role, emailVerified), request, response);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/logout")
    ResponseEntity<Void> logout(HttpServletRequest request, HttpServletResponse response) {
        loginSessionManager.logout(request, response);
        return ResponseEntity.noContent().build();
    }
}
