package com.moewadeul.member.application;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Component
public class MemberMetrics {

    private final Counter signups;
    private final Counter emailVerifications;

    public MemberMetrics(MeterRegistry meterRegistry) {
        this.signups = Counter.builder("moewadeul.member.signups")
                .description("가입을 마친 회원 수")
                .register(meterRegistry);
        this.emailVerifications = Counter.builder("moewadeul.member.email.verifications")
                .description("이메일 인증을 마친 회원 수")
                .register(meterRegistry);
    }

    public void recordSignup() {
        afterCommit(signups);
    }

    public void recordEmailVerified() {
        afterCommit(emailVerifications);
    }

    private static void afterCommit(Counter counter) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            counter.increment();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                counter.increment();
            }
        });
    }
}
