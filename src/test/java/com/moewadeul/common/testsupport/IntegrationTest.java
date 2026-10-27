package com.moewadeul.common.testsupport;

import com.moewadeul.common.mail.TestMailSenderConfig;
import com.moewadeul.outbox.application.RecordingOutboxEventHandler;
import com.moewadeul.weather.application.WeatherCacheResetExtension;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.context.annotation.Import;
import org.springframework.core.annotation.AliasFor;
import org.springframework.test.context.ActiveProfiles;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Tag("integration")
@SpringBootTest
@ActiveProfiles("test")
@Import({
    MySqlTestContainerConfig.class,
    TestClockConfig.class,
    TestMailSenderConfig.class,
    TestImageStorageConfig.class,
    RecordingOutboxEventHandler.Config.class,
    WireMockTestConfig.class
})
@ExtendWith({
    DatabaseCleanupExtension.class,
    ClockResetExtension.class,
    WireMockResetExtension.class,
    ImageStorageResetExtension.class,
    WeatherCacheResetExtension.class
})
public @interface IntegrationTest {

    @AliasFor(annotation = SpringBootTest.class, attribute = "webEnvironment")
    WebEnvironment webEnvironment() default WebEnvironment.MOCK;
}
