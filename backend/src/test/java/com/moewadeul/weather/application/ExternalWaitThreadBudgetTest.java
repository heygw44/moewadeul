package com.moewadeul.weather.application;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.constructors;
import static org.assertj.core.api.Assertions.assertThat;

import com.moewadeul.aisearch.infra.OpenAiSpotQueryInterpreter;
import com.moewadeul.common.external.ExternalApiProperties;
import com.moewadeul.common.external.ExternalCallLimit;
import com.moewadeul.common.storage.S3ImageStorage;
import com.moewadeul.weather.infra.KasiClient;
import com.moewadeul.weather.infra.KmaClient;
import com.moewadeul.weather.infra.KmaProperties;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;

class ExternalWaitThreadBudgetTest {

    private static final int DB_ONLY_REQUEST_THREADS = 10;

    @Test
    @DisplayName("[NFR-05] 외부 응답을 기다리는 스레드를 모두 더해도 운영 Tomcat 스레드에 DB만 쓰는 요청의 몫이 남는다")
    void externalWaitsLeaveRoomForDbOnlyRequests() throws IOException {
        Binder binder = prodBinder();
        int kma = binder.bindOrCreate("moewadeul.weather.kma", KmaProperties.class)
                .maxConcurrentCalls();
        int perApi = binder.bindOrCreate("moewadeul.external-api", ExternalApiProperties.class)
                .maxConcurrentCalls();
        int tomcatThreads = binder.bind("server.tomcat.threads.max", Bindable.of(Integer.class))
                .get();

        int kasi = perApi;
        int openAi = perApi;
        int s3Head = perApi;
        int externalWaits = WeatherCaches.MAX_WAITERS + kma + kasi + openAi + s3Head;

        assertThat(tomcatThreads - externalWaits)
                .as(
                        "외부 대기 %d개에 DB만 쓰는 요청 몫 %d개를 더한 만큼 application-prod.yml의 server.tomcat.threads.max를 올린다",
                        externalWaits, DB_ONLY_REQUEST_THREADS)
                .isGreaterThanOrEqualTo(DB_ONLY_REQUEST_THREADS);
    }

    @Test
    @DisplayName("[NFR-05] 동시 호출 한도를 새로 쓰는 외부 API는 스레드 계산에 함께 넣는다")
    void everyExternalCallLimitIsCountedInTheBudget() {
        constructors()
                .that()
                .areDeclaredIn(ExternalCallLimit.class)
                .should()
                .onlyBeCalled()
                .byClassesThat()
                .belongToAnyOf(
                        KmaClient.class, KasiClient.class, OpenAiSpotQueryInterpreter.class, S3ImageStorage.class)
                .check(new ClassFileImporter()
                        .withImportOption(new ImportOption.DoNotIncludeTests())
                        .importPackages("com.moewadeul"));
    }

    private static Binder prodBinder() throws IOException {
        var loader = new YamlPropertySourceLoader();
        List<PropertySource<?>> sources = new ArrayList<>();
        sources.addAll(loader.load("prod", new ClassPathResource("application-prod.yml")));
        sources.addAll(loader.load("base", new ClassPathResource("application.yml")));
        return new Binder(ConfigurationPropertySources.from(sources));
    }
}
