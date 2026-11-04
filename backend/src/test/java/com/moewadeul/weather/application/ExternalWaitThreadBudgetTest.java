package com.moewadeul.weather.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.moewadeul.common.external.ExternalApiProperties;
import com.moewadeul.weather.infra.KmaProperties;
import java.io.IOException;
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

        assertThat(tomcatThreads - externalWaits).isGreaterThanOrEqualTo(DB_ONLY_REQUEST_THREADS);
    }

    private static Binder prodBinder() throws IOException {
        var loader = new YamlPropertySourceLoader();
        Iterable<PropertySource<?>> sources = loader.load("prod", new ClassPathResource("application-prod.yml"));
        return new Binder(ConfigurationPropertySources.from(sources));
    }
}
