plugins {
    java
    jacoco
    id("org.springframework.boot") version "4.1.2"
    id("io.spring.dependency-management") version "1.1.7"
    id("com.diffplug.spotless") version "8.10.4"
}

group = "com.moewadeul"
version = "0.0.1-SNAPSHOT"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

repositories {
    mavenCentral()
}

// Spring Boot 4.1.1이 정한 Tomcat과 Jackson 버전에 알려진 취약점(Tomcat 보안 제약 우회, Jackson 서비스 거부 등)이 있다.
// 4.1.1이 아직 최신 4.1 버전이라 Boot가 관리하는 버전 속성을 고친 버전으로 덮어쓴다. Boot가 같은 버전 이상을 관리하게 되면 이 블록을 지운다.
extra["tomcat.version"] = "11.0.26"
extra["jackson-bom.version"] = "3.1.7"
extra["jackson-2-bom.version"] = "2.21.7"

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-webmvc")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-flyway")
    implementation("org.flywaydb:flyway-mysql")
    implementation("org.hibernate.orm:hibernate-spatial")
    implementation("org.mybatis.spring.boot:mybatis-spring-boot-starter:4.1.0")
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:3.1.1")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-session-jdbc")
    implementation("org.springframework.boot:spring-boot-starter-mail")
    // 기상청·천문연 결과를 메모리에 잠시 둔다. 버전은 Spring Boot가 관리한다.
    implementation("com.github.ben-manes.caffeine:caffeine")
    // AWS SDK는 Spring Boot가 버전을 관리하지 않는다. BOM으로 SDK 모듈들의 버전을 하나로 맞춘다.
    implementation(platform("software.amazon.awssdk:bom:2.57.2"))
    implementation("software.amazon.awssdk:s3")

    compileOnly("org.projectlombok:lombok")
    annotationProcessor("org.projectlombok:lombok")
    runtimeOnly("com.mysql:mysql-connector-j")
    runtimeOnly("io.micrometer:micrometer-registry-prometheus")

    testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
    testImplementation("org.springframework.boot:spring-boot-starter-security-test")
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("org.testcontainers:testcontainers-mysql")
    testImplementation("com.tngtech.archunit:archunit:1.5.1")
    testImplementation("org.wiremock:wiremock-standalone:3.13.2")
    testCompileOnly("org.projectlombok:lombok")
    testAnnotationProcessor("org.projectlombok:lombok")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<Test> {
    useJUnitPlatform()
}

tasks.test {
    useJUnitPlatform {
        excludeTags("integration", "ai-eval")
    }
}

val integrationTest by tasks.registering(Test::class) {
    description = "Testcontainers가 필요한 통합 테스트를 실행한다. Docker가 필요하다."
    group = "verification"
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    useJUnitPlatform {
        includeTags("integration")
    }
    // 우리는 UTC가 아닌 JVM에서도 시각이 UTC로 저장되는지 확인하려고, 일부러 한국 표준시(KST)로 JVM을 띄운다.
    jvmArgs("-Duser.timezone=Asia/Seoul")
    // 캐시된 스프링 컨텍스트가 늘면 기본 512MB 힙에서 KmaClientIntegrationTest가 OOM이나 시간 초과로 끝났다(#839).
    maxHeapSize = "1g"
    shouldRunAfter(tasks.test)
}

// 실제 OpenAI를 호출해 문장 해석 정확도를 재는 평가다. 비용이 들어서 check에는 넣지 않고, OPENAI_API_KEY를 넣고 직접 실행한다.
val aiSearchEval by tasks.registering(Test::class) {
    description = "OpenAI를 실제로 호출해 AI 검색 문장 해석 정확도를 평가한다. OPENAI_API_KEY가 필요하다."
    group = "verification"
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    useJUnitPlatform {
        includeTags("ai-eval")
    }
    outputs.upToDateWhen { false }
    testLogging {
        showStandardStreams = true
    }
}

// 운영 이미지(Dockerfile)는 build/libs에 있는 jar 하나를 가져간다. 그래서 실행할 수 없는 plain jar는 만들지 않는다.
tasks.jar {
    enabled = false
}

jacoco {
    // JaCoCo는 Java 25 클래스 파일을 0.8.14부터 정식 지원한다.
    toolVersion = "0.8.15"
}

tasks.jacocoTestReport {
    // 단위 테스트와 통합 테스트의 실행 정보를 한 리포트로 합친다. 참고용이라 우리는 기준선을 두지 않는다.
    dependsOn(tasks.test, integrationTest)
    executionData(tasks.test.get(), integrationTest.get())
    reports {
        xml.required = true
        html.required = true
    }
}

tasks.check {
    dependsOn(integrationTest, tasks.jacocoTestReport)
}

spotless {
    java {
        target("src/**/*.java")
        palantirJavaFormat("2.101.0")
        removeUnusedImports()
        trimTrailingWhitespace()
        endWithNewline()
    }
}
