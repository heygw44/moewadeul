package com.moewadeul.common.logging;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.LoggingEvent;
import ch.qos.logback.classic.util.LogbackMDCAdapter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.sql.SQLIntegrityConstraintViolationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.logging.logback.StructuredLogEncoder;
import org.springframework.core.env.Environment;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mock.env.MockEnvironment;

class ProductionLogRedactionTest {

    private static final String VICTIM_EMAIL = "victim@example.com";

    @Test
    @DisplayName("[NFR-11] 운영 로그 형식은 처리하지 못한 제약 위반의 원인 메시지에 든 값을 지우고 어떤 제약인지는 남긴다")
    void uncaughtConstraintViolationDoesNotLeakValue() throws IOException {
        Throwable uncaught = new IllegalStateException(
                "transaction failed",
                new SQLIntegrityConstraintViolationException(
                        "Duplicate entry '" + VICTIM_EMAIL + "' for key 'member.uk_member_email'", "23000", 1062));

        String logLine = encodeWithProductionSettings("unexpected exception", uncaught);

        assertThat(logLine).doesNotContain(VICTIM_EMAIL).contains("uk_member_email");
    }

    @Test
    @DisplayName("[NFR-11] 운영 로그 형식은 메시지 본문에 든 제약 위반 값도 지운다")
    void valueInLogMessageIsRedacted() throws IOException {
        String logLine = encodeWithProductionSettings(
                "lock failed: Duplicate entry '" + VICTIM_EMAIL + "' for key 'member.uk_member_email'", null);

        assertThat(logLine).doesNotContain(VICTIM_EMAIL).contains("uk_member_email");
    }

    private static String encodeWithProductionSettings(String message, Throwable throwable) throws IOException {
        MockEnvironment environment = new MockEnvironment();
        new YamlPropertySourceLoader()
                .load("application-prod", new ClassPathResource("application-prod.yml"))
                .forEach(source -> environment.getPropertySources().addFirst(source));
        LoggerContext context = new LoggerContext();
        context.setMDCAdapter(new LogbackMDCAdapter());
        context.putObject(Environment.class.getName(), environment);
        StructuredLogEncoder encoder = new StructuredLogEncoder();
        encoder.setFormat(environment.getProperty("logging.structured.format.console"));
        encoder.setContext(context);
        encoder.start();
        LoggingEvent event = new LoggingEvent(
                ProductionLogRedactionTest.class.getName(),
                context.getLogger("test"),
                Level.ERROR,
                message,
                throwable,
                null);
        return new String(encoder.encode(event), StandardCharsets.UTF_8);
    }
}
