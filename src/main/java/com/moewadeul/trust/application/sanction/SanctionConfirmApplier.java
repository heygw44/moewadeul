package com.moewadeul.trust.application.sanction;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
class SanctionConfirmApplier {

    private final SanctionConfirmWriter sanctionConfirmWriter;

    @Transactional
    SanctionConfirmResult apply(SanctionConfirmCommand command) {
        return sanctionConfirmWriter.confirm(command);
    }
}
