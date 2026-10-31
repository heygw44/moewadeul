#!/bin/sh
# 운영 프로필로 컨텍스트만 띄웠다가 바로 끝낸다. DB·Redis·외부 API 없이 돈다.
# Dockerfile의 AOT 학습 실행과 CI check가 같이 쓴다. 테스트 프로필이 끄는 조건부 빈의 기동 실패를 PR에서 잡으려는 것이다(#886).
set -eu

jar="$1"
shift

exec env SPRING_PROFILES_ACTIVE=prod \
    DB_URL=jdbc:mysql://127.0.0.1:3306/moewadeul DB_USERNAME=training DB_PASSWORD=training \
    SPRING_FLYWAY_ENABLED=false SPRING_JPA_HIBERNATE_DDL_AUTO=none \
    SPRING_JPA_PROPERTIES_HIBERNATE_BOOT_ALLOW_JDBC_METADATA_ACCESS=false \
    SPRING_JPA_DATABASE_PLATFORM=org.hibernate.dialect.MySQLDialect \
    CI_HMAC_KEY=training-run-only-not-a-real-key-0000000000000000 \
    MAIL_SECRET_HMAC_KEY=training-run-only-not-a-real-key-0000000000000000 \
    UPLOAD_BUCKET=training OPENAI_API_KEY=training PUBLICDATA_SERVICE_KEY=training \
    MAIL_USERNAME=training MAIL_APP_PASSWORD=training \
    java "$@" -Dspring.context.exit=onRefresh -jar "$jar"
