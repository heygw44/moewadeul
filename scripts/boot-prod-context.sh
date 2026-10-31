#!/bin/sh
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
