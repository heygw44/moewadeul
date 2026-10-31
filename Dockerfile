# 운영용 앱 이미지다. jar는 CI가 ./gradlew bootJar로 먼저 만들고, 이 파일은 그 jar를 받아 이미지로 묶기만 한다.
# 이미지 안에서 Gradle로 빌드하지 않는 이유: CI의 Gradle 캐시를 그대로 쓸 수 있고, 이미지에 JDK가 들어가지 않는다.

FROM eclipse-temurin:25-jre AS extract
WORKDIR /work
COPY build/libs/*.jar app.jar
# Spring Boot 도구 모드로 jar를 의존성, 로더, 스냅숏 의존성, 애플리케이션 층으로 나눈다.
# 코드만 바뀐 배포에서는 서버가 맨 아래 애플리케이션 층만 새로 받는다.
RUN java -Djarmode=tools -jar app.jar extract --layers --destination extracted

FROM eclipse-temurin:25-jre
RUN groupadd --system app && useradd --system --gid app --no-create-home app
WORKDIR /app
COPY --from=extract /work/extracted/dependencies/ ./
COPY --from=extract /work/extracted/spring-boot-loader/ ./
COPY --from=extract /work/extracted/snapshot-dependencies/ ./
COPY --from=extract /work/extracted/application/ ./
# 기동 시간을 줄이려고 빌드 때 앱을 한 번 띄워 Java AOT 캐시를 만든다. 로컬 운영 조건(CPU 2개, 768MB)에서 기동이 약 30% 줄었다(#852).
RUN --mount=type=bind,source=scripts/boot-prod-context.sh,target=/tmp/boot-prod-context.sh \
    sh /tmp/boot-prod-context.sh app.jar -XX:AOTCacheOutput=app.aot
USER app
EXPOSE 8080
# 컨테이너 메모리 한도의 40%까지만 힙으로 쓴다. 한도 768MB면 힙 최대는 약 308MB다.
# 힙 밖(메타스페이스, 코드 캐시, 스레드 스택, 네이티브 메모리)이 부하 때 약 420MB까지 커져서,
# 75%(약 576MB)로 두면 힙이 다 자랐을 때 합이 한도를 넘는다. 부하 중 힙 사용은 약 320MB에서 멈췄다.
# 힙이 모자라면 JVM을 끝낸다. 끝내지 않으면 OutOfMemoryError로 Tomcat의 연결 수락 스레드가 죽은 채 프로세스만 남는다.
# 그러면 앱은 요청을 받지 않는데 컨테이너는 Up이라 Docker가 다시 띄우지 않는다. JVM이 끝나면 compose의 restart 설정대로 다시 뜬다.
# 캐시를 쓰면 메타스페이스가 약 100MB 줄고 힙 확보분이 약 80MB 는다. 지도 조회 부하에서 힙 밖 메모리는 캐시가 없을 때와 같아서 40% 설정을 그대로 둔다(#852).
ENTRYPOINT ["java", "-XX:AOTCache=app.aot", "-XX:MaxRAMPercentage=40", "-XX:+ExitOnOutOfMemoryError", "-jar", "app.jar"]
