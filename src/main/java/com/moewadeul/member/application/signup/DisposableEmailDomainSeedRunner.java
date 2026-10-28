package com.moewadeul.member.application.signup;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DisposableEmailDomainSeedRunner {

    private final DisposableEmailDomainSeedService seedService;

    @EventListener(ApplicationReadyEvent.class)
    public void seedOnStartup() {
        seedService.seedPublicDomains();
    }
}
